"""Fase 05 — Compras: fornecedores + recebimento de mercadoria (goods_receipt) espalhados pelos
12 meses, sempre ANTES de qualquer venda daquele SKU (a fase 07 só vende o que já foi "recebido"
até a data simulada). Um subconjunto dos recebimentos também vira um registro de auditoria de
importação de NF-e (nfe_import/nfe_import_line), já CONFIRMED e vinculado ao goods_receipt.

Os XMLs em data/nfe-imports/ são fixtures sintéticas minúsculas (não NF-e reais) — não usadas
como fonte de dados aqui; os registros de auditoria são gerados diretamente em SQL.
"""
import random
import sys
from datetime import timedelta

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import gen_cnpj, money, read_json, sqn, sqs, sqts, write_json, write_sql

SUPPLIERS = [
    "Distribuidora Fumaça Livre Ltda",
    "Zomo Importação e Distribuição S.A.",
    "Atacado Hookah Brasil Ltda",
    "Bem Bolado Distribuidora Nacional",
    "Casa do Fumante Atacado Ltda",
    "Squadafum Comércio e Distribuição S.A.",
]


def quantity_tier(rng, sale_price):
    if sale_price < 15:
        return rng.randint(60, 180)
    if sale_price < 50:
        return rng.randint(30, 90)
    if sale_price < 150:
        return rng.randint(10, 40)
    return rng.randint(3, 15)


def spaced_dates(rng, n):
    bucket = config.WINDOW_DAYS / n
    dates = []
    for i in range(n):
        lo = i * bucket
        hi = (i + 1) * bucket
        offset = rng.uniform(lo, max(hi - 1, lo + 0.5))
        dates.append(config.START_DATE + timedelta(days=offset, hours=rng.uniform(7, 17)))
    return sorted(dates)


def main():
    rng = random.Random(config.RNG_SEED + 2)
    catalogo = read_json(config.out_path("catalogo.json"))
    operators = read_json(config.out_path("usuarios.json"))
    usernames = [o["username"] for o in operators]

    suppliers = [{"legal_name": name, "tax_id": gen_cnpj(rng), "email": f"compras@{name.split()[0].lower()}.com.br"} for name in SUPPLIERS]

    receipts = []
    for p in catalogo["products"]:
        n_events = rng.randint(2, 4)
        dates = spaced_dates(rng, n_events)
        for received_at in dates:
            days_in = (received_at - config.START_DATE).days
            time_factor = 0.95 + 0.13 * (days_in / config.WINDOW_DAYS)
            unit_cost = money(p["cost_price"] * time_factor * rng.uniform(0.93, 1.04))
            # Proporção de recebimento por depósito acompanha o mix de canal das vendas (fase 07:
            # ~70% BALCAO/LOJA-01, ~30% MARKETPLACE/CD-ECOM-01), senão o marketplace fica sem
            # estoque suficiente para sustentar seu volume de pedidos.
            warehouse_code = config.WAREHOUSE_ECOM if rng.random() < 0.30 else config.WAREHOUSE_LOJA
            supplier = rng.choice(suppliers)
            receipts.append(
                {
                    "sku": p["sku"],
                    "quantity": quantity_tier(rng, p["sale_price"]),
                    "unit_cost": float(unit_cost),
                    "received_at": received_at,
                    "warehouse_code": warehouse_code,
                    "supplier_tax_id": supplier["tax_id"],
                    "username": rng.choice(usernames),
                    "barcode": p.get("barcode"),
                }
            )
    receipts.sort(key=lambda r: r["received_at"])

    # Marca ~10 recebimentos para também virarem auditoria de importação de NF-e.
    nfe_flagged_idx = set(rng.sample(range(len(receipts)), k=min(10, len(receipts))))

    statements = ["-- Fase 05: fornecedores + recebimento de mercadoria (compras)", ""]
    for s in suppliers:
        statements.append(
            "INSERT INTO supplier (legal_name, tax_id, email, active) VALUES ("
            f"{sqs(s['legal_name'])}, {sqs(s['tax_id'])}, {sqs(s['email'])}, TRUE) "
            "ON CONFLICT (tax_id) DO NOTHING;"
        )
    statements.append("")

    for i, r in enumerate(receipts):
        statements.append(
            "INSERT INTO goods_receipt (supplier_id, warehouse_code, username, received_at) "
            f"SELECT s.id, {sqs(r['warehouse_code'])}, {sqs(r['username'])}, {sqts(r['received_at'])} "
            f"FROM supplier s WHERE s.tax_id = {sqs(r['supplier_tax_id'])} "
            "RETURNING id AS gr_id \\gset"
        )
        statements.append(
            "INSERT INTO goods_receipt_item (goods_receipt_id, sku, quantity, unit_cost) VALUES ("
            f":gr_id, {sqs(r['sku'])}, {r['quantity']}, {sqn(r['unit_cost'])});"
        )
        if i in nfe_flagged_idx:
            uploaded_at = r["received_at"] - timedelta(hours=rng.uniform(1, 6))
            statements.append(
                "INSERT INTO nfe_import (supplier_id, emitter_cnpj, warehouse_code, file_reference, "
                "status, goods_receipt_id, uploaded_by, uploaded_at, confirmed_at) "
                f"SELECT s.id, s.tax_id, {sqs(r['warehouse_code'])}, "
                f"{sqs(f'nfe-imports/demo-{i:05d}.xml')}, 'CONFIRMED', :gr_id, {sqs(r['username'])}, "
                f"{sqts(uploaded_at)}, {sqts(r['received_at'])} "
                f"FROM supplier s WHERE s.tax_id = {sqs(r['supplier_tax_id'])} "
                "RETURNING id AS nfe_id \\gset"
            )
            statements.append(
                "INSERT INTO nfe_import_line (nfe_import_id, item_number, supplier_product_code, ean, "
                "description, quantity, unit_price, match_status, matched_sku) VALUES ("
                f":nfe_id, 1, {sqs('FORN-' + r['sku'])}, {sqs(r['barcode'])}, {sqs('Recebimento ' + r['sku'])}, "
                f"{r['quantity']}, {sqn(r['unit_cost'])}, 'MATCHED', {sqs(r['sku'])});"
            )
    statements.append("")

    write_sql(config.out_path("05_fornecedores_compras.sql"), statements)
    write_json(
        config.out_path("compras.json"),
        {"suppliers": suppliers, "receipts": receipts},
    )
    print(f"05_fornecedores_compras: {len(suppliers)} fornecedores, {len(receipts)} recebimentos, {len(nfe_flagged_idx)} com NF-e.")


if __name__ == "__main__":
    main()
