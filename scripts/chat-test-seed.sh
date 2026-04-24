#!/usr/bin/env bash
# =============================================================================
# chat-test-seed.sh
#
# Seed completo para testar o modulo de chat da AllSet API.
# Cria usuarios, catalogo, profissional, endereco e executa o fluxo Express
# ate o passo que cria a conversa automaticamente (client-respond).
#
# Pre-requisitos:
#   - API rodando em http://localhost:8080
#   - curl instalado
#   - jq instalado (brew install jq / apt install jq / winget install jqlang.jq)
#
# Uso:
#   chmod +x scripts/chat-test-seed.sh
#   ./scripts/chat-test-seed.sh
# =============================================================================

set -euo pipefail

BASE_URL="http://localhost:8080"
BOLD="\033[1m"
GREEN="\033[0;32m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
CYAN="\033[0;36m"
RESET="\033[0m"

# --- helpers -----------------------------------------------------------------

step() { echo -e "\n${BOLD}${CYAN}> $1${RESET}"; }
ok()   { echo -e "  ${GREEN}OK $1${RESET}"; }
fail() { echo -e "  ${RED}ERRO: $1${RESET}"; exit 1; }

# Usa printf para garantir encoding UTF-8 correto no Windows/Git Bash
post() {
  local path="$1"
  local token="$2"
  local body="$3"
  printf '%s' "$body" | curl -s -X POST "${BASE_URL}${path}" \
    -H "Content-Type: application/json; charset=utf-8" \
    ${token:+-H "Authorization: Bearer $token"} \
    --data-binary @-
}

get() {
  local path="$1"
  local token="$2"
  curl -s -X GET "${BASE_URL}${path}" \
    ${token:+-H "Authorization: Bearer $token"}
}

patch_req() {
  local path="$1"
  local token="$2"
  local body="${3:-}"
  if [ -n "$body" ]; then
    printf '%s' "$body" | curl -s -X PATCH "${BASE_URL}${path}" \
      -H "Content-Type: application/json; charset=utf-8" \
      ${token:+-H "Authorization: Bearer $token"} \
      --data-binary @-
  else
    curl -s -X PATCH "${BASE_URL}${path}" \
      ${token:+-H "Authorization: Bearer $token"}
  fi
}

put_req() {
  local path="$1"
  local token="$2"
  local body="$3"
  printf '%s' "$body" | curl -s -X PUT "${BASE_URL}${path}" \
    -H "Content-Type: application/json; charset=utf-8" \
    ${token:+-H "Authorization: Bearer $token"} \
    --data-binary @-
}

assert_field() {
  local label="$1"
  local value="$2"
  if [ -z "$value" ] || [ "$value" = "null" ]; then
    fail "$label veio vazio ou null. Verifique a API."
  fi
}

# --- verificar que a API esta no ar ------------------------------------------

step "Verificando API em ${BASE_URL}"
health=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health")
if [ "$health" != "200" ]; then
  fail "API nao esta respondendo (HTTP $health). Rode './mvnw spring-boot:run' primeiro."
fi
ok "API respondendo"

# =============================================================================
# FLUXO 1 - Criar usuarios
# =============================================================================

step "Criando usuario CLIENTE"
client_resp=$(post "/api/users" "" '{"name":"Cliente Seed","cpf":"529.982.247-25","email":"cliente.seed@allset.test","phone":"+5585999990001","password":"Senha@2025!","role":"client"}')
CLIENT_USER_ID=$(echo "$client_resp" | jq -r '.id')
assert_field "client_user_id" "$CLIENT_USER_ID"
ok "id: $CLIENT_USER_ID"

step "Criando usuario PROFISSIONAL"
pro_user_resp=$(post "/api/users" "" '{"name":"Profissional Seed","cpf":"111.444.777-35","email":"profissional.seed@allset.test","phone":"+5585999990002","password":"Senha@2025!","role":"professional"}')
PRO_USER_ID=$(echo "$pro_user_resp" | jq -r '.id')
assert_field "professional_user_id" "$PRO_USER_ID"
ok "id: $PRO_USER_ID"

step "Criando usuario ADMIN"
admin_resp=$(post "/api/users" "" '{"name":"Admin Seed","cpf":"123.456.789-09","email":"admin.seed@allset.test","phone":"+5585999990003","password":"Senha@2025!","role":"admin"}')
ADMIN_USER_ID=$(echo "$admin_resp" | jq -r '.id')
assert_field "admin_user_id" "$ADMIN_USER_ID"
ok "id: $ADMIN_USER_ID"

# =============================================================================
# FLUXO 2 - Login
# =============================================================================

step "Login CLIENTE"
client_login=$(post "/api/auth/login" "" '{"email":"cliente.seed@allset.test","password":"Senha@2025!"}')
CLIENT_TOKEN=$(echo "$client_login" | jq -r '.accessToken')
assert_field "client_access_token" "$CLIENT_TOKEN"
ok "Token obtido"

step "Login PROFISSIONAL"
pro_login=$(post "/api/auth/login" "" '{"email":"profissional.seed@allset.test","password":"Senha@2025!"}')
PRO_TOKEN=$(echo "$pro_login" | jq -r '.accessToken')
assert_field "professional_access_token" "$PRO_TOKEN"
ok "Token obtido"

step "Login ADMIN"
admin_login=$(post "/api/auth/login" "" '{"email":"admin.seed@allset.test","password":"Senha@2025!"}')
ADMIN_TOKEN=$(echo "$admin_login" | jq -r '.accessToken')
assert_field "admin_access_token" "$ADMIN_TOKEN"
ok "Token obtido"

# =============================================================================
# FLUXO 3 - Catalogo e plano
# =============================================================================

step "Criando area de servico"
area_resp=$(post "/api/v1/service-areas" "$ADMIN_TOKEN" '{"name":"Eletrica Seed","iconUrl":"https://cdn.example.com/icons/eletrica.png"}')
AREA_ID=$(echo "$area_resp" | jq -r '.id')
assert_field "area_id" "$AREA_ID"
ok "id: $AREA_ID"

step "Criando categoria de servico"
cat_body=$(printf '{"areaId":"%s","name":"Eletricista Seed","iconUrl":"https://cdn.example.com/icons/eletricista.png"}' "$AREA_ID")
cat_resp=$(post "/api/v1/service-categories" "$ADMIN_TOKEN" "$cat_body")
CATEGORY_ID=$(echo "$cat_resp" | jq -r '.id')
assert_field "category_id" "$CATEGORY_ID"
ok "id: $CATEGORY_ID"

step "Criando plano de assinatura Pro"
plan_resp=$(post "/api/v1/subscription-plans" "$ADMIN_TOKEN" '{"name":"Plano Pro Seed","priceMonthly":49.90,"highlightInSearch":true,"expressPriority":true,"badgeLabel":"Pro","active":true}')
PLAN_ID=$(echo "$plan_resp" | jq -r '.id')
assert_field "subscription_plan_id" "$PLAN_ID"
ok "id: $PLAN_ID"

# =============================================================================
# FLUXO 4 - Onboarding do profissional
# =============================================================================

step "Criando perfil profissional"
pro_body=$(printf '{"userId":"%s","bio":"Eletricista residencial e comercial - seed","yearsOfExperience":7,"baseHourlyRate":150.00}' "$PRO_USER_ID")
pro_profile_resp=$(post "/api/v1/professionals" "" "$pro_body")
PROFESSIONAL_ID=$(echo "$pro_profile_resp" | jq -r '.id')
assert_field "professional_id" "$PROFESSIONAL_ID"
ok "id: $PROFESSIONAL_ID"

step "Enviando documento do profissional"
doc_resp=$(post "/api/v1/professionals/$PROFESSIONAL_ID/documents" "$PRO_TOKEN" '{"docType":"rg","fileUrl":"https://cdn.example.com/documents/rg-seed.jpg"}')
DOC_ID=$(echo "$doc_resp" | jq -r '.id')
assert_field "document_id" "$DOC_ID"
ok "id: $DOC_ID"

step "Atribuindo plano Pro ao profissional"
plan_body=$(printf '{"subscriptionPlanId":"%s"}' "$PLAN_ID")
put_req "/api/v1/professionals/$PROFESSIONAL_ID/subscription" "$PRO_TOKEN" "$plan_body" > /dev/null
ok "Plano atribuido"

step "Ativando geolocalizacao do profissional (Fortaleza)"
patch_req "/api/v1/professionals/$PROFESSIONAL_ID/geo" "$PRO_TOKEN" '{"geoActive":true,"geoLat":-3.731862,"geoLng":-38.526669}' > /dev/null
ok "Geo ativado"

step "Aprovando verificacao KYC (admin)"
patch_req "/api/v1/professionals/$PROFESSIONAL_ID/verify" "$ADMIN_TOKEN" '{"status":"approved","rejectionReason":null}' > /dev/null
ok "Profissional verificado"

step "Criando servico oferecido"
offering_body=$(printf '{"categoryId":"%s","title":"Instalacao de tomadas - seed","description":"Instalacao e troca de tomadas e interruptores","pricingType":"fixed","price":180.00,"estimatedDurationMinutes":90}' "$CATEGORY_ID")
offering_resp=$(post "/api/v1/professionals/$PROFESSIONAL_ID/services" "$PRO_TOKEN" "$offering_body")
OFFERING_ID=$(echo "$offering_resp" | jq -r '.id')
assert_field "offering_id" "$OFFERING_ID"
ok "id: $OFFERING_ID"

# =============================================================================
# FLUXO 5 - Endereco do cliente
# =============================================================================

step "Criando endereco do cliente"
addr_body=$(printf '{"label":"Casa Seed","street":"Rua das Flores","number":"42","complement":"Apto 301","district":"Centro","city":"Fortaleza","state":"CE","zipCode":"60000-000","lat":-3.731862,"lng":-38.526669,"isDefault":true}')
addr_resp=$(post "/api/users/$CLIENT_USER_ID/addresses" "$CLIENT_TOKEN" "$addr_body")
ADDRESS_ID=$(echo "$addr_resp" | jq -r '.id')
assert_field "address_id" "$ADDRESS_ID"
ok "id: $ADDRESS_ID"

# =============================================================================
# FLUXO 6 - Pedido Express -> cria a conversa
# =============================================================================

step "Criando pedido Express"
order_body=$(printf '{"areaId":"%s","categoryId":"%s","description":"Tomada queimada na sala - seed de teste do chat","addressId":"%s","photoUrl":"https://cdn.example.com/orders/problema-seed.jpg","urgencyFee":20.00}' "$AREA_ID" "$CATEGORY_ID" "$ADDRESS_ID")
order_resp=$(post "/api/v1/orders/express" "$CLIENT_TOKEN" "$order_body")
ORDER_ID=$(echo "$order_resp" | jq -r '.id')
assert_field "order_id" "$ORDER_ID"
ok "id: $ORDER_ID"

step "Profissional responde ao pedido com proposta"
post "/api/v1/orders/$ORDER_ID/express/pro-respond" "$PRO_TOKEN" '{"response":"accepted","proposedAmount":180.00}' > /dev/null
ok "Proposta enviada - R$ 180,00"

step "Cliente escolhe a proposta -> conversa criada automaticamente"
client_respond_body=$(printf '{"selectedProfessionalId":"%s"}' "$PROFESSIONAL_ID")
client_respond_resp=$(post "/api/v1/orders/$ORDER_ID/express/client-respond" "$CLIENT_TOKEN" "$client_respond_body")
ORDER_STATUS=$(echo "$client_respond_resp" | jq -r '.status')
ok "Order status: $ORDER_STATUS"

step "Buscando conversation_id"
conversations_resp=$(get "/api/v1/conversations" "$CLIENT_TOKEN")
CONVERSATION_ID=$(echo "$conversations_resp" | jq -r '.content[0].id')
assert_field "conversation_id" "$CONVERSATION_ID"
ok "id: $CONVERSATION_ID"

# =============================================================================
# RESULTADO FINAL
# =============================================================================

echo ""
echo -e "${BOLD}${GREEN}=====================================================${RESET}"
echo -e "${BOLD}${GREEN}  SEED CONCLUIDO - variaveis para uso nos testes${RESET}"
echo -e "${BOLD}${GREEN}=====================================================${RESET}"
echo ""
echo -e "${BOLD}IDs:${RESET}"
echo "  CLIENT_USER_ID    = $CLIENT_USER_ID"
echo "  PRO_USER_ID       = $PRO_USER_ID"
echo "  ADMIN_USER_ID     = $ADMIN_USER_ID"
echo "  PROFESSIONAL_ID   = $PROFESSIONAL_ID"
echo "  ORDER_ID          = $ORDER_ID"
echo "  CONVERSATION_ID   = $CONVERSATION_ID"
echo ""
echo -e "${BOLD}Tokens (validos por 15 min):${RESET}"
echo "  CLIENT_TOKEN  = $CLIENT_TOKEN"
echo "  PRO_TOKEN     = $PRO_TOKEN"
echo "  ADMIN_TOKEN   = $ADMIN_TOKEN"
echo ""
echo -e "${BOLD}${CYAN}--- Comandos prontos para copiar ---${RESET}"
echo ""
echo -e "${CYAN}# Listar mensagens:${RESET}"
printf 'curl -s "%s/api/v1/conversations/%s/messages" \\\n' "$BASE_URL" "$CONVERSATION_ID"
printf '  -H "Authorization: Bearer %s" | jq\n' "$CLIENT_TOKEN"
echo ""
echo -e "${CYAN}# Enviar mensagem (profissional):${RESET}"
printf 'curl -s -X POST "%s/api/v1/conversations/%s/messages" \\\n' "$BASE_URL" "$CONVERSATION_ID"
printf '  -H "Authorization: Bearer %s" \\\n' "$PRO_TOKEN"
printf '  -H "Content-Type: application/json" \\\n'
printf '  -d '"'"'{"content":"Estou a caminho!"}'"'"' | jq\n'
echo ""
echo -e "${CYAN}# Marcar como lido (cliente):${RESET}"
printf 'curl -s -X PATCH "%s/api/v1/conversations/%s/read" \\\n' "$BASE_URL" "$CONVERSATION_ID"
printf '  -H "Authorization: Bearer %s" | jq\n' "$CLIENT_TOKEN"
echo ""
echo -e "${CYAN}# WebSocket - conectar com wscat:${RESET}"
printf 'wscat -c "ws://localhost:8080/ws/websocket"\n'
echo ""
echo -e "${CYAN}# Apos conectar, colar no terminal wscat (Enter duplo no final):${RESET}"
printf 'CONNECT\nAuthorization:Bearer %s\naccept-version:1.2\nheart-beat:0,0\n\n' "$CLIENT_TOKEN"
echo ""
echo -e "${CYAN}# Depois inscrever na conversa:${RESET}"
printf 'SUBSCRIBE\nid:sub-0\ndestination:/topic/conversations/%s\n\n' "$CONVERSATION_ID"
echo ""
