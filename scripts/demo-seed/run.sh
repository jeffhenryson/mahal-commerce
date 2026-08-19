#!/usr/bin/env bash
# run.sh — gera e aplica o pipeline de dados de demonstração contra um Postgres de hml.
#
# Uso:
#   MAHAL_SEED_TARGET=hml DB_HOST=localhost DB_PORT=5435 DB_NAME=mahal \
#     DB_USERNAME=postgres DB_PASSWORD=*** ./run.sh
#
# Guarda de segurança: exige MAHAL_SEED_TARGET=hml explicitamente (nunca lê .env de dev/prod
# automaticamente) e pede confirmação interativa antes do TRUNCATE, a menos que rodado com
# --yes (uso não interativo/CI).

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ "${MAHAL_SEED_TARGET:-}" != "hml" ]]; then
  echo "ERRO: defina MAHAL_SEED_TARGET=hml explicitamente antes de rodar este script." >&2
  echo "Isto existe para nunca apontar essa carga (destrutiva) para prod por engano." >&2
  exit 1
fi

: "${DB_HOST:?defina DB_HOST}"
: "${DB_PORT:?defina DB_PORT}"
: "${DB_NAME:?defina DB_NAME}"
: "${DB_USERNAME:?defina DB_USERNAME}"
: "${DB_PASSWORD:?defina DB_PASSWORD}"

AUTO_YES=false
for arg in "$@"; do
  if [[ "$arg" == "--yes" ]]; then AUTO_YES=true; fi
done

if [[ "$AUTO_YES" != "true" ]]; then
  echo "Isto vai APAGAR (TRUNCATE) as tabelas de negócio em ${DB_HOST}:${DB_PORT}/${DB_NAME} e"
  echo "recarregar com dados de demonstração gerados do zero."
  read -r -p "Digite CONFIRMO para continuar: " confirm
  if [[ "$confirm" != "CONFIRMO" ]]; then
    echo "Cancelado."
    exit 1
  fi
fi

export PGPASSWORD="$DB_PASSWORD"
PSQL=(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 --no-psqlrc)

echo "==> Gerando SQL das fases 01-09 (Python)..."
mkdir -p out
python3 01_usuarios.py
python3 02_warehouses.py
python3 03_catalogo.py
python3 04_clientes_crm.py
python3 05_fornecedores_compras.py
python3 06_estoque_inicial.py
python3 07_vendas.py
python3 08_estoque_reposicao.py
python3 09_financeiro.py

echo "==> Aplicando 00_reset.sql..."
"${PSQL[@]}" -f 00_reset.sql

for f in out/01_usuarios.sql out/02_warehouses.sql out/03_catalogo.sql out/04_clientes_crm.sql \
         out/05_fornecedores_compras.sql out/06_estoque_inicial.sql out/07_vendas.sql \
         out/08_estoque_reposicao.sql out/09_financeiro.sql; do
  echo "==> Aplicando $f..."
  "${PSQL[@]}" -f "$f"
done

echo "==> Rodando validação (10_validacao.sql)..."
"${PSQL[@]}" -f 10_validacao.sql

echo "==> Concluído. Senha de todas as contas de demonstração: veja config.py (DEMO_PASSWORD)."
