"""Fase 02 — Depósitos: nenhuma migration cria um warehouse, então a pipeline precisa criar pelo
menos 1 antes de qualquer outra fase (compras/estoque/PDV dependem de warehouse_code/warehouse_id).
"""
import sys

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import sqb, sqs, write_json, write_sql

WAREHOUSES = [
    {"code": config.WAREHOUSE_LOJA, "name": "Loja Física - Centro", "type": "LOJA_FISICA"},
    {"code": config.WAREHOUSE_ECOM, "name": "Centro de Distribuição E-commerce", "type": "ECOMMERCE"},
]


def main():
    statements = ["-- Fase 02: depósitos", ""]
    for w in WAREHOUSES:
        statements.append(
            "INSERT INTO warehouse (code, name, type, active) VALUES ("
            f"{sqs(w['code'])}, {sqs(w['name'])}, {sqs(w['type'])}, TRUE"
            ") ON CONFLICT (code) DO NOTHING;"
        )
    statements.append("")

    write_sql(config.out_path("02_warehouses.sql"), statements)
    write_json(config.out_path("warehouses.json"), WAREHOUSES)
    print(f"02_warehouses: {len(WAREHOUSES)} depósitos gerados.")


if __name__ == "__main__":
    main()
