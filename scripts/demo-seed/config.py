"""Constantes compartilhadas pelo pipeline de seed de demonstração."""
from datetime import datetime, timedelta
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
OUT_DIR = BASE_DIR / "out"

RNG_SEED = 20260818

# Janela de histórico simulada: hoje (data corrente do ambiente) menos 12 meses.
TODAY = datetime(2026, 8, 18, 12, 0, 0)
WINDOW_DAYS = 365
START_DATE = TODAY - timedelta(days=WINDOW_DAYS)

# Escala "robusta" combinada com o usuário.
N_SIMPLES = 150
N_KITS = 40
N_CUSTOMERS = 500
N_OPERATORS = 25
CUSTOMER_ACCOUNT_RATIO = 0.15  # ~75 clientes ganham login vinculado (user_type=CUSTOMER)
N_ORDERS = 1500

# Toda conta fictícia usa a mesma senha de demonstração, para facilitar o login ao vivo.
DEMO_PASSWORD = "Demo@2026"
DEMO_EMAIL_DOMAIN = "mahaldemo.local"
DEMO_USER_PREFIX = "demo."

# Conta de administrador com nome fixo (fora do prefixo demo.*, sobrevive ao DELETE seletivo de
# 00_reset.sql) — é o login "de vitrine" da demonstração, pedido explicitamente pelo usuário.
ADMIN_USERNAME = "administrador"
ADMIN_PASSWORD = "Administrador@2026!"
ADMIN_EMAIL = "administrador@mahaldemo.local"

WAREHOUSE_LOJA = "LOJA-01"
WAREHOUSE_ECOM = "CD-ECOM-01"

# Bate com a taxa GLOBAL semeada por V70__cashback.sql e com system_config (carência/expiração).
CASHBACK_PERCENT = "3.0000"
CASHBACK_CARENCIA_DIAS = 7
CASHBACK_EXPIRACAO_DIAS = 180

# Mix de canal/fluxo das vendas (fase 07).
BALCAO_SHARE = 0.70
COMANDA_SHARE_OF_BALCAO = 0.20
RESERVADO_SHARE_OF_BALCAO = 0.05
REEMBOLSADO_SHARE_OF_BALCAO = 0.05

MARKETPLACE_CANCELADO_SHARE = 0.15
MARKETPLACE_REEMBOLSADO_SHARE = 0.10
MARKETPLACE_RECENT_DAYS = 10  # janela recente que mostra o pipeline "ao vivo" em andamento

# Pontos de reposição / alerta de estoque baixo (fase 08).
N_SKUS_WITH_REORDER_POINT = 25
N_SKUS_BELOW_REORDER_POINT = 8


def out_path(filename: str) -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    return OUT_DIR / filename
