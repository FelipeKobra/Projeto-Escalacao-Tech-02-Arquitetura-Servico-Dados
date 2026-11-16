#!/usr/bin/env bash
# =============================================================================
# Verifica, ponta a ponta, cada item do Projeto1.md (ver docs/evidencias.md).
#
#   bash scripts/verificar-entrega.sh             # documentos + stack + pipeline + marts
#   bash scripts/verificar-entrega.sh --so-docs   # só os documentos (não precisa de Docker)
#   bash scripts/verificar-entrega.sh --derrubar  # derruba a stack no final
#
# Windows: rode no Git Bash ou no WSL. Sai com código 0 só se tudo passou.
# =============================================================================
set -uo pipefail
cd "$(dirname "$0")/.."

SO_DOCS=false
DERRUBAR=false
for arg in "$@"; do
  case "$arg" in
    --so-docs) SO_DOCS=true ;;
    --derrubar) DERRUBAR=true ;;
    *) echo "Opção desconhecida: $arg" >&2; exit 2 ;;
  esac
done

readonly ESPERA_SIMULADOR_S=90
readonly ESPERA_PIPELINE_S=120
PASSOU=0
FALHOU=0

ok()    { printf '  [OK]    %s\n' "$1"; PASSOU=$((PASSOU + 1)); }
falha() { printf '  [FALHA] %s\n' "$1"; FALHOU=$((FALHOU + 1)); }
secao() { printf '\n== %s ==\n' "$1"; }
checar() { if eval "$2"; then ok "$1"; else falha "$1"; fi; }

# Documento existe, não está vazio e não tem mais TODO.
doc_pronto() { [ -s "$1" ] && ! grep -q 'TODO' "$1"; }

# Seção "## <título...>" de um documento tem texto e nenhum TODO.
secao_pronta() {
  local conteudo
  conteudo=$(awk -v t="## $2" 'index($0, t) == 1 {f = 1; next} /^## / {f = 0} f' "$1" | grep -v '^[[:space:]]*$')
  [ -n "$conteudo" ] && ! grep -q 'TODO' <<<"$conteudo"
}

resumo() {
  printf '\n== Resultado: %d OK, %d FALHA ==\n' "$PASSOU" "$FALHOU"
  [ "$FALHOU" -eq 0 ] && echo "Tudo entregue." || echo "Veja docs/evidencias.md para o que falta e quem é o responsável."
  if $DERRUBAR && ! $SO_DOCS; then docker compose down >/dev/null 2>&1; fi
  [ "$FALHOU" -eq 0 ]
  exit $?
}

# ---------------------------------------------------------------- documentos
secao "Documentos (itens obrigatórios e entregáveis)"
checar "[Felipe] Definição do negócio: docs/cenario.md"          'doc_pronto docs/cenario.md'
checar "[Felipe] Atores produtores e consumidores: docs/atores.md" 'doc_pronto docs/atores.md'
checar "[Romulo] Diagrama com legenda: docs/arquitetura.md"       'doc_pronto docs/arquitetura.md && grep -qi "legenda" docs/arquitetura.md'
checar "[Romulo] Diagrama exportado: docs/img/arquitetura.png"    '[ -s docs/img/arquitetura.png ]'
checar "[Felipe] Seção Raw justificada"                          'secao_pronta docs/camadas-dw.md "Raw"'
checar "[Romulo] Seções Curada e Agregada justificadas"          'secao_pronta docs/camadas-dw.md "Curada" && secao_pronta docs/camadas-dw.md "Agregada"'
checar "[Romulo] Justificativa DW x banco relacional"            'secao_pronta docs/camadas-dw.md "Por que um DW"'
checar "[Romulo] Pelo menos 2 Data Marts: docs/data-marts.md"     'doc_pronto docs/data-marts.md && [ "$(grep -c "^## Mart" docs/data-marts.md)" -ge 2 ]'
checar "[Felipe] Ciclo de vida do dado: docs/ciclo-de-vida.md"    'doc_pronto docs/ciclo-de-vida.md'
checar "[Todos] Projeto1.md mantido no repositório"               '[ -s Projeto1.md ]'

$SO_DOCS && resumo

# ---------------------------------------------------------------- stack
secao "Orquestração (Docker Compose)"
if ! command -v docker >/dev/null 2>&1; then
  falha "[Base] Docker instalado"; resumo
fi
[ -f .env ] || { cp .env.example .env; echo "  (criado .env a partir do .env.example)"; }

env_ou() { local v; v=$(grep -E "^$1=" .env | tail -1 | cut -d= -f2-); echo "${v:-$2}"; }
FONTES="http://localhost:$(env_ou FONTES_PORTA 8081)"
PLATAFORMA="http://localhost:$(env_ou PLATAFORMA_PORTA 8080)"
DW_USER=$(env_ou DW_DB_USER farmarede)

echo "  subindo a stack (o primeiro build demora alguns minutos)..."
if docker compose up -d --build --wait --wait-timeout 400 >/dev/null 2>&1; then
  ok "[Base] Containers oltp-db, dw-db, fontes-operacionais e plataforma-dados healthy"
else
  falha "[Base] Stack não ficou healthy (veja: docker compose logs)"; resumo
fi

sql_dw() { docker compose exec -T dw-db psql -U "$DW_USER" -d dw -tAc "$1" 2>/dev/null | tr -d '[:space:]'; }
http() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
maior_que_zero() { [ -n "$1" ] && [ "$1" -gt 0 ] 2>/dev/null; }

schemas_ok() {
  [ "$(sql_dw "select count(*) from information_schema.schemata where schema_name in ('raw', 'curated', 'marts', 'controle')")" = "4" ]
}
tabelas_marts() { sql_dw "select count(*) from information_schema.tables where table_schema = 'marts'"; }

checar "[Base] DW com schemas raw, curated, marts e controle" 'schemas_ok'

# ---------------------------------------------------------------- coleta
secao "Coleta (produtores simulados)"
vendas=0
for _ in $(seq 1 $((ESPERA_SIMULADOR_S / 5))); do
  vendas=$(curl -s "$FONTES/fontes/resumo" | grep -o '"vendas":[0-9]*' | cut -d: -f2)
  maior_que_zero "$vendas" && break
  sleep 5
done
checar "[Felipe] Simulador gerando vendas no OLTP (vendas=${vendas:-0})" 'maior_que_zero "$vendas"'

# ---------------------------------------------------------------- pipeline
secao "Pipeline: coleta → processamento → agregação"
codigo=$(http -X POST "$PLATAFORMA/pipeline/execucoes")
checar "[Felipe] POST /pipeline/execucoes aceito (HTTP $codigo)" '[ "$codigo" = "202" ]'

etapa_ok() { [ "$(sql_dw "select count(*) from controle.execucao_pipeline where etapa = '$1' and status = 'SUCESSO'")" -gt 0 ] 2>/dev/null; }
[ "$codigo" = "202" ] && for _ in $(seq 1 $((ESPERA_PIPELINE_S / 5))); do
  etapa_ok agregacao-marts && break
  etapa_ok ingestao-oltp-raw && [ "$(sql_dw "select count(*) from controle.execucao_pipeline where status = 'FALHA'")" -gt 0 ] 2>/dev/null && break
  sleep 5
done
checar "[Felipe] Etapa 10 ingestao-oltp-raw com SUCESSO"     'etapa_ok ingestao-oltp-raw'
checar "[Felipe] Camada Raw com dados (raw.venda)"           'maior_que_zero "$(sql_dw "select count(*) from raw.venda")"'
checar "[Romulo] Etapa 20 transformacao-curated com SUCESSO" 'etapa_ok transformacao-curated'
checar "[Romulo] Camada Curada com dados (curated.fato_venda)" 'maior_que_zero "$(sql_dw "select count(*) from curated.fato_venda")"'
checar "[Romulo] Etapa 30 agregacao-marts com SUCESSO"       'etapa_ok agregacao-marts'
checar "[Romulo] Pelo menos 2 tabelas de Data Mart (schema marts)" \
  '[ "$(tabelas_marts)" -ge 2 ] 2>/dev/null'

# ---------------------------------------------------------------- consumo
secao "Consumo (API dos Data Marts)"
checar "[Felipe] GET /pipeline/execucoes (histórico)"   '[ "$(http "$PLATAFORMA/pipeline/execucoes")" = "200" ]'
checar "[Romulo] GET /marts/vendas/diarias"             '[ "$(http "$PLATAFORMA/marts/vendas/diarias")" = "200" ]'
checar "[Romulo] GET /marts/vendas/top-produtos"        '[ "$(http "$PLATAFORMA/marts/vendas/top-produtos")" = "200" ]'
checar "[Romulo] GET /marts/estoque/ruptura"            '[ "$(http "$PLATAFORMA/marts/estoque/ruptura")" = "200" ]'
checar "[Romulo] GET /marts/estoque/giro"               '[ "$(http "$PLATAFORMA/marts/estoque/giro")" = "200" ]'

resumo
