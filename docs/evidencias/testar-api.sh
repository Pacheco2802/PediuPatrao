#!/usr/bin/env bash
# Teste manual da API do dashboard com curl (aplicacao rodando em localhost:8080).
# Uso: bash docs/evidencias/testar-api.sh > docs/evidencias/api-respostas.txt
# Os usuarios sao os de teste criados pelo DataInitializer (ver README).

BASE="http://localhost:8080"
TMP="$(mktemp -d)"

# faz login pelo formulario (precisa do token CSRF da tela de login)
login() {
    local jar="$TMP/$1.txt"
    local token
    token=$(curl -s -c "$jar" "$BASE/login" | grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed 's/.*value="\([^"]*\)"/\1/')
    curl -s -o /dev/null -b "$jar" -c "$jar" \
        --data-urlencode "username=$1" --data-urlencode "password=$2" --data-urlencode "_csrf=$token" \
        "$BASE/login"
    echo "$jar"
}

# chama um endpoint e mostra status HTTP + corpo
chamar() {
    local titulo="$1" jar="$2" caminho="$3"
    echo "------------------------------------------------------------"
    echo "$titulo"
    echo "GET $caminho"
    if [ -n "$jar" ]; then
        curl -s -b "$jar" -w '\nHTTP %{http_code}\n' "$BASE$caminho"
    else
        curl -s -w '\nHTTP %{http_code}\n' "$BASE$caminho"
    fi
    echo
}

GERENTE=$(login gerente01 gerente123)
ADMIN=$(login admin teste123)
ATENDENTE=$(login atendente01 atendente123)

echo "EVIDENCIAS - API DO DASHBOARD ($(date '+%d/%m/%Y %H:%M'))"
echo

echo "===== 1. CONTROLE DE ACESSO ====="
chamar "CT01 - sem login deve dar 401" "" "/api/dashboard/indicadores"
chamar "CT02 - ATENDENTE deve dar 403" "$ATENDENTE" "/api/dashboard/indicadores"
chamar "CT03 - ADMIN deve dar 200" "$ADMIN" "/api/dashboard/filtros"

echo "===== 2. ENDPOINTS (GERENTE, periodo padrao de 7 dias) ====="
chamar "CT04 - indicadores gerais (RF01)" "$GERENTE" "/api/dashboard/indicadores"
chamar "CT05 - vendas por dia (RF02)" "$GERENTE" "/api/dashboard/vendas-por-dia"
chamar "CT06 - vendas por hora (RF02)" "$GERENTE" "/api/dashboard/vendas-por-hora"
chamar "CT07 - vendas por categoria (RF02)" "$GERENTE" "/api/dashboard/vendas-por-categoria"
chamar "CT08 - pedidos por status (RF03)" "$GERENTE" "/api/dashboard/pedidos-por-status"
chamar "CT09 - pedidos recentes, limite 3 (RF03)" "$GERENTE" "/api/dashboard/pedidos-recentes?limite=3"
chamar "CT10 - produtos mais vendidos, limite 3 (RF04)" "$GERENTE" "/api/dashboard/produtos-mais-vendidos?limite=3"

echo "===== 3. FILTROS (RF05) ====="
HOJE=$(date +%F)
chamar "CT11 - somente hoje" "$GERENTE" "/api/dashboard/indicadores?dataInicio=$HOJE&dataFim=$HOJE"
chamar "CT12 - somente RETIRADA" "$GERENTE" "/api/dashboard/indicadores?tipoEntrega=RETIRADA"
chamar "CT13 - somente CANCELADO (faturamento deve ser 0)" "$GERENTE" "/api/dashboard/indicadores?status=CANCELADO"
chamar "CT14 - periodo sem pedidos (tudo zerado, sem erro)" "$GERENTE" "/api/dashboard/indicadores?dataInicio=2020-01-01&dataFim=2020-01-07"

echo "===== 4. ERROS ====="
chamar "CT15 - data em formato errado deve dar 400" "$GERENTE" "/api/dashboard/indicadores?dataInicio=10/11/2026"
chamar "CT16 - data inicial maior que a final deve dar 400" "$GERENTE" "/api/dashboard/vendas-por-dia?dataInicio=$HOJE&dataFim=2020-01-01"
chamar "CT17 - periodo maior que 366 dias deve dar 400" "$GERENTE" "/api/dashboard/vendas-por-dia?dataInicio=2020-01-01&dataFim=$HOJE"
chamar "CT18 - status inexistente deve dar 400" "$GERENTE" "/api/dashboard/pedidos-por-status?status=ENTREGUE"
chamar "CT19 - tipo de entrega inexistente deve dar 400" "$GERENTE" "/api/dashboard/indicadores?tipoEntrega=MOTOBOY"
chamar "CT20 - limite fora da faixa deve dar 400" "$GERENTE" "/api/dashboard/produtos-mais-vendidos?limite=0"
chamar "CT21 - limite que nao e numero deve dar 400" "$GERENTE" "/api/dashboard/produtos-mais-vendidos?limite=abc"

echo "------------------------------------------------------------"
echo "CT22 - POST no dashboard (somente leitura) deve dar 403"
echo "POST /api/dashboard/indicadores"
curl -s -o /dev/null -b "$GERENTE" -X POST -w 'HTTP %{http_code}\n' "$BASE/api/dashboard/indicadores"

rm -rf "$TMP"
