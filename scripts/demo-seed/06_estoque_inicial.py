"""Fase 06 — Estoque (entradas): grava o ledger `stock_movement` tipo ENTRADA para cada
recebimento de mercadoria gerado na fase 05.

`stock_balance` NÃO é escrito aqui: o saldo final por SKU/depósito só é conhecido depois de somar
também as saídas de venda (fase 07), que é quem calcula e grava o `stock_balance` consolidado —
average_cost é sensível à ordem cronológica de entradas E saídas juntas, não só das entradas.
"""
import sys
from datetime import datetime

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import read_json, sqn, sqs, sqts, write_sql


def main():
    compras = read_json(config.out_path("compras.json"))
    receipts = compras["receipts"]
    for r in receipts:
        r["received_at"] = datetime.fromisoformat(r["received_at"])

    statements = ["-- Fase 06: ledger de entradas de estoque (derivado das compras da fase 05)", ""]
    for r in receipts:
        statements.append(
            "INSERT INTO stock_movement (sku, warehouse_id, type, quantity, reason, username, created_at, unit_cost) "
            "SELECT "
            f"{sqs(r['sku'])}, w.id, 'ENTRADA', {r['quantity']}, 'Recebimento de mercadoria (compra)', "
            f"{sqs(r['username'])}, {sqts(r['received_at'])}, {sqn(r['unit_cost'])} "
            f"FROM warehouse w WHERE w.code = {sqs(r['warehouse_code'])};"
        )
    statements.append("")

    write_sql(config.out_path("06_estoque_inicial.sql"), statements)
    print(f"06_estoque_inicial: {len(receipts)} movimentos de ENTRADA gerados.")


if __name__ == "__main__":
    main()
