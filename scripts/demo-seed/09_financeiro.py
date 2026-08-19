"""Fase 09 — Financeiro: fluxo de caixa (cash_flow_entry). Não há nenhum listener que gere
lançamentos automaticamente a partir de sales_order/goods_receipt — FinanceiroService.createEntry
é escrita 100% manual — então esta fase escreve os lançamentos explicitamente:
  - INFLOW mensal agregado por canal (VENDA_PDV/VENDA_ECOMMERCE), a partir do resumo de vendas
    da fase 07 (não há como referenciar sales_order.id individual: não é conhecido em tempo de
    geração, só quando o INSERT roda).
  - OUTFLOW recorrente mensal (aluguel, folha, pró-labore, TI, energia/água).
  - OUTFLOW por recebimento de mercadoria (FORNECEDOR_PRODUTO), amostra dos recebimentos da fase
    05, com entity_name = fornecedor.
"""
import random
import sys
from datetime import datetime, timedelta

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import money, read_json, sqd, sqn, sqs, write_sql

RECURRING_OUTFLOWS = [
    ("ALUGUEL", "Aluguel do ponto comercial", 2500, 4000, 5),
    ("FOLHA_PAGAMENTO", "Folha de pagamento da equipe", 15000, 24000, 5),
    ("PRO_LABORE", "Pró-labore dos sócios", 3000, 6000, 5),
    ("SOFTWARE_TI", "Assinaturas de software e TI", 300, 800, 10),
    ("ENERGIA_AGUA", "Energia elétrica e água", 600, 1500, 15),
]


def month_range():
    months = []
    d = config.START_DATE.date().replace(day=1)
    end = config.TODAY.date()
    while d <= end:
        months.append(d)
        if d.month == 12:
            d = d.replace(year=d.year + 1, month=1)
        else:
            d = d.replace(month=d.month + 1)
    return months


def status_for_due_date(rng, due_date):
    today = config.TODAY.date()
    if due_date > today:
        return "PREVISTO", None
    if due_date < today - timedelta(days=10):
        status = rng.choices(["PAGO", "ATRASADO"], weights=[92, 8])[0]
    else:
        status = rng.choices(["PAGO", "ATRASADO", "PREVISTO"], weights=[60, 25, 15])[0]
    payment_date = due_date - timedelta(days=rng.randint(0, 5)) if status == "PAGO" else None
    return status, payment_date


def main():
    rng = random.Random(config.RNG_SEED + 5)
    compras = read_json(config.out_path("compras.json"))
    vendas = read_json(config.out_path("vendas_resumo.json"))
    for o in vendas:
        o["sold_at"] = datetime.fromisoformat(o["sold_at"])

    suppliers_by_tax_id = {s["tax_id"]: s for s in compras["suppliers"]}
    months = month_range()

    statements = ["-- Fase 09: financeiro (fluxo de caixa)", ""]

    # ---- INFLOW mensal agregado por canal --------------------------------------------------
    revenue_states = {"CONCLUIDO", "RESERVADO", "PAGO", "SEPARADO", "ENVIADO", "ENTREGUE", "REEMBOLSADO"}
    for m in months:
        month_end = (m.replace(day=28) + timedelta(days=4)).replace(day=1) - timedelta(days=1)
        for channel, category in (("BALCAO", "VENDA_PDV"), ("MARKETPLACE", "VENDA_ECOMMERCE")):
            total = sum(
                o["net_amount"] for o in vendas
                if o["channel"] == channel and o["status"] in revenue_states
                and o["sold_at"].year == m.year and o["sold_at"].month == m.month
            )
            if total <= 0:
                continue
            due = min(month_end, config.TODAY.date())
            status, payment_date = ("PAGO", due) if due < config.TODAY.date() else ("PREVISTO", None)
            label = "PDV/balcão" if channel == "BALCAO" else "marketplace"
            month_label = m.strftime("%m/%Y")
            description = f"Receita de vendas {label} - {month_label}"
            statements.append(
                "INSERT INTO cash_flow_entry (date, description, entity_name, category, direction, amount, "
                "status, due_date, payment_date, linked_entity_type, linked_entity_id) VALUES ("
                f"{sqd(due)}, {sqs(description)}, NULL, "
                f"{sqs(category)}, 'INFLOW', {sqn(round(total, 2))}, {sqs(status)}, {sqd(due)}, "
                f"{sqd(payment_date)}, NULL, NULL);"
            )
    statements.append("")

    # ---- OUTFLOW recorrente mensal ----------------------------------------------------------
    for m in months:
        for category, desc, lo, hi, due_day in RECURRING_OUTFLOWS:
            due_date = m.replace(day=min(due_day, 28))
            amount = round(rng.uniform(lo, hi), 2)
            status, payment_date = status_for_due_date(rng, due_date)
            description = f"{desc} - {m.strftime('%m/%Y')}"
            statements.append(
                "INSERT INTO cash_flow_entry (date, description, entity_name, category, direction, amount, "
                "status, due_date, payment_date, linked_entity_type, linked_entity_id) VALUES ("
                f"{sqd(due_date)}, {sqs(description)}, NULL, {sqs(category)}, 'OUTFLOW', "
                f"{sqn(amount)}, {sqs(status)}, {sqd(due_date)}, {sqd(payment_date)}, NULL, NULL);"
            )
    statements.append("")

    # ---- OUTFLOW por recebimento de mercadoria (amostra) -------------------------------------
    receipts = compras["receipts"]
    sample = rng.sample(receipts, k=min(120, len(receipts)))
    for r in sample:
        received_at = datetime.fromisoformat(r["received_at"]) if isinstance(r["received_at"], str) else r["received_at"]
        due_date = received_at.date() + timedelta(days=30)
        amount = round(r["quantity"] * r["unit_cost"], 2)
        if amount <= 0:
            continue
        status, payment_date = status_for_due_date(rng, due_date)
        supplier = suppliers_by_tax_id.get(r["supplier_tax_id"])
        entity_name = supplier["legal_name"] if supplier else "Fornecedor"
        description = f"Recebimento de mercadoria - {r['sku']}"
        statements.append(
            "INSERT INTO cash_flow_entry (date, description, entity_name, category, direction, amount, "
            "status, due_date, payment_date, linked_entity_type, linked_entity_id) VALUES ("
            f"{sqd(received_at.date())}, {sqs(description)}, {sqs(entity_name)}, "
            f"'FORNECEDOR_PRODUTO', 'OUTFLOW', {sqn(amount)}, {sqs(status)}, {sqd(due_date)}, "
            f"{sqd(payment_date)}, NULL, NULL);"
        )
    statements.append("")

    write_sql(config.out_path("09_financeiro.sql"), statements)
    print(f"09_financeiro: {len(months)} meses de recorrentes, {len(sample)} lançamentos de compra.")


if __name__ == "__main__":
    main()
