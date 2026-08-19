"""Fase 08 — Alertas de estoque: pontos de reposição para um subconjunto de SKUs e um ajuste
final proposital do saldo de alguns deles para ficarem abaixo do próprio ponto de reposição —
para a demo mostrar o alerta de estoque mínimo disparando de verdade.
"""
import random
import sys

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import read_json, sqs, write_sql


def min_qty_for(sale_price, rng):
    if sale_price < 15:
        return rng.randint(30, 50)
    if sale_price < 50:
        return rng.randint(15, 30)
    if sale_price < 150:
        return rng.randint(5, 15)
    return rng.randint(2, 6)


def main():
    rng = random.Random(config.RNG_SEED + 4)
    catalogo = read_json(config.out_path("catalogo.json"))
    products = catalogo["products"]

    chosen = rng.sample(products, k=min(config.N_SKUS_WITH_REORDER_POINT, len(products)))
    reorder_points = [{"sku": p["sku"], "min_quantity": min_qty_for(p["sale_price"], rng)} for p in chosen]

    below_target = rng.sample(reorder_points, k=min(config.N_SKUS_BELOW_REORDER_POINT, len(reorder_points)))

    statements = ["-- Fase 08: pontos de reposição + estoque baixo proposital", ""]
    for rp in reorder_points:
        statements.append(
            "INSERT INTO stock_reorder_point (sku, warehouse_id, min_quantity) "
            f"SELECT {sqs(rp['sku'])}, w.id, {rp['min_quantity']} FROM warehouse w "
            f"WHERE w.code = {sqs(config.WAREHOUSE_LOJA)} "
            "ON CONFLICT (sku, warehouse_id) DO UPDATE SET min_quantity = EXCLUDED.min_quantity;"
        )
    statements.append("")

    for rp in below_target:
        deficit = rng.randint(1, 5)
        target_qty = max(rp["min_quantity"] - deficit, 0)
        statements.append(
            "UPDATE stock_balance SET quantity = "
            f"{target_qty} "
            f"WHERE sku = {sqs(rp['sku'])} AND warehouse_id = "
            f"(SELECT id FROM warehouse WHERE code = {sqs(config.WAREHOUSE_LOJA)});"
        )
    statements.append("")

    write_sql(config.out_path("08_estoque_reposicao.sql"), statements)
    print(f"08_estoque_reposicao: {len(reorder_points)} pontos de reposição, {len(below_target)} SKUs abaixo do mínimo.")


if __name__ == "__main__":
    main()
