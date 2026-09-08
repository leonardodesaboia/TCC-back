# Sprint — cadastro de endereço confiável

A branch parte de `refactor/geolocalization` e fecha as regressões encontradas na validação integrada. Endereços aproximados continuam úteis como sugestão, mas não entram no Express sem confirmação. O raio padrão foi alinhado aos 300 m informados pelo produto.

Corrigido: cache reverso preservando o pin exato, invalidação por mudança de bairro, preservação quando os dados não mudam e repasse das configurações pelo Compose. No frontend, edição preserva confiança/acurácia, mudanças no endereço exigem reconfirmação e consultas atrasadas não substituem um pin mais recente.

Validação final em 08/09/2026: 185 testes backend aprovados, incluindo PostgreSQL/Redis reais, migrations, login, endereços, Express, chat, notificações e avaliações; 41 testes frontend e TypeScript aprovados. Dados antigos das fixtures foram atualizados para os contratos atuais, mantendo as validações de produção.

Smoke HTTP reproduzível: `scripts/test-geolocation-local.ps1`, com o frontend e backend em execução e a seed local habilitada. Testa procedência, rejeição do centroide no Express, confirmação manual, edição e cache. Uma amostra pública real encontrou UNIFOR e Dom Luís como ROOFTOP e Beira Mar como INTERPOLATED; os três pontos são distintos. Isso não é uma garantia de precisão para qualquer endereço nem uma repetição da amostra histórica de 20 casos.

## Subir tudo localmente

Preencha `.env` conforme `.env.example`, incluindo chaves locais válidas e `GEOCODING_USER_AGENT`. Com os repositórios lado a lado e ambos nesta branch:

```powershell
.\scripts\start-local.ps1
```

O script aceita `-FrontendPath` e um arquivo adicional `-EnvironmentOverride`. Aguarda a saúde dos cinco containers, sobe o front em http://localhost:8081 e verifica o proxy para a API em http://localhost:8080. Use a seed apenas em desenvolvimento.

## Limite da verificação

Interface web e APIs foram executadas localmente. Permissão/retorno do GPS são cobertos pela lógica automatizada; captura física de GPS em aparelho Android/iOS não foi executada nesta máquina.
