"""Fase 07 — Vendas: o simulador central. Gera ~1500 sales_order (BALCAO via caixa/comanda,
MARKETPLACE via cliente) ao longo dos 12 meses, com sazonalidade, e produz TUDO que uma venda
real produziria: order_item com custo/nome congelados, order_payment, cashback_entry (só para
cliente com CPF, replicando Customer.isOfficiallyRegistered()), stock_movement de SAÍDA (kit
explode nos componentes, nunca move o próprio SKU do kit), e o stock_balance FINAL consolidado
(calculado incrementalmente, entradas — lidas de compras.json — e saídas juntas, em ordem
cronológica, com a mesma fórmula de custo médio ponderado móvel do domínio).

Referências entre INSERTs usam variáveis psql (`RETURNING id AS x \\gset` + `:x`), não IDs
pré-calculados — funciona porque o arquivo inteiro roda numa sessão `psql` só.
"""
import random
import sys
from datetime import datetime, timedelta

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import money, read_json, sqb, sqn, sqs, sqts, write_json, write_sql

HOUR_WEIGHTS = [
    0, 0, 0, 0, 0, 0, 0, 0, 0,      # 0h-8h: loja fechada
    0.3, 0.4, 0.5, 0.6, 0.7, 0.8,   # 9h-14h
    1.3, 1.4, 1.3, 1.2,             # 15h-18h: pico de tarde
    1.0, 0.9, 0.7,                  # 19h-21h: pico noturno
    0.4, 0.1,                       # 22h-23h
]
WEEKDAY_FACTOR = [0.8, 0.85, 0.9, 1.0, 1.3, 1.5, 1.1]  # segunda..domingo

TABLE_LABELS = [
    "Mesa 1", "Mesa 2", "Mesa 3", "Mesa 4", "Mesa 5", "Área Externa 1", "Área Externa 2",
    "Narguilé Lounge A", "Narguilé Lounge B", "Balcão - Cliente",
]
SANGRIA_REASONS = ["Depósito bancário", "Pagamento de fornecedor à vista", "Troco para outro caixa", "Retirada do sócio"]
SUPRIMENTO_REASONS = ["Reforço de troco", "Aporte inicial adicional"]
CANCEL_REASONS = ["Cliente desistiu antes do pagamento", "Estoque insuficiente no fechamento", "Pedido duplicado"]
REFUND_REASONS = ["Produto com defeito", "Cliente desistiu da compra", "Troca não disponível, reembolso efetuado", "Erro no lançamento do pedido"]
PAYMENT_METHODS_BALCAO = (["DINHEIRO"] * 35 + ["DEBITO"] * 25 + ["CREDITO"] * 25 + ["PIX"] * 15)
PAYMENT_METHODS_MKT = (["PIX"] * 40 + ["CREDITO"] * 45 + ["DEBITO"] * 15)


class StockSim:
    def __init__(self):
        self.balance = {}
        self.avg_cost = {}

    def available(self, sku, wh):
        return self.balance.get((sku, wh), 0)

    def cost_at(self, sku, wh):
        return self.avg_cost.get((sku, wh))

    def apply_entrada(self, sku, wh, qty, unit_cost):
        key = (sku, wh)
        q0 = self.balance.get(key, 0)
        if unit_cost is not None:
            a0 = self.avg_cost.get(key) or 0
            new_qty = q0 + qty
            self.avg_cost[key] = ((q0 * a0) + (qty * unit_cost)) / new_qty if new_qty > 0 else None
        self.balance[key] = q0 + qty

    def apply_saida(self, sku, wh, qty):
        key = (sku, wh)
        newq = max(self.balance.get(key, 0) - qty, 0)
        self.balance[key] = newq
        if newq == 0:
            self.avg_cost[key] = None


def cap_future(dt):
    """Nunca deixa um timestamp calculado ultrapassar 'agora' (config.TODAY)."""
    limit = config.TODAY - timedelta(minutes=30)
    return dt if dt <= limit else limit


def customer_ref(cust):
    if cust is None:
        return "NULL"
    return f"(SELECT id FROM customers WHERE {cust['lookup_col']} = {sqs(cust['lookup_val'])})"


def warehouse_ref(code):
    return f"(SELECT id FROM warehouse WHERE code = {sqs(code)})"


def build_calendar(rng):
    days = []
    d = config.START_DATE.date()
    end = config.TODAY.date()
    while d < end:
        days.append(d)
        d += timedelta(days=1)
    closed = set(rng.sample(days, k=max(int(len(days) * 0.05), 1)))
    return [d for d in days if d not in closed]


def day_weight(d):
    month_factor = 1.2 if d.month == 12 else (0.85 if d.month == 2 else 1.0)
    return WEEKDAY_FACTOR[d.weekday()] * month_factor


def pick_operator(rng, operators, roles=("ROLE_ATENDENTE", "ROLE_ADMIN")):
    pool = [o for o in operators if o["role"] in roles] or operators
    return rng.choice(pool)["username"]


def build_sessions(rng, operating_days, operators):
    sessions = {}
    for idx, d in enumerate(operating_days):
        opened_at = datetime(d.year, d.month, d.day, rng.randint(9, 10), rng.randint(0, 59))
        hours_open = rng.uniform(10, 11) if d.weekday() < 5 else rng.uniform(12, 13)
        closed_at = opened_at + timedelta(hours=hours_open)
        operator = pick_operator(rng, operators)
        closed_by = operator if rng.random() < 0.9 else pick_operator(rng, operators)

        movements = []
        for _ in range(rng.choice([0, 1, 1, 2, 2, 3])):
            typ = rng.choices(["SANGRIA", "SUPRIMENTO"], weights=[70, 30])[0]
            reason = rng.choice(SANGRIA_REASONS if typ == "SANGRIA" else SUPRIMENTO_REASONS)
            when = opened_at + timedelta(hours=rng.uniform(0.5, max(hours_open - 0.2, 0.6)))
            # Sangria pequena o bastante para dificilmente estourar o fundo de troco do dia — o
            # limite duro fica no clamp de emit_sessions_sql, que conhece o total de vendas real.
            amount = money(rng.uniform(20, 80)) if typ == "SANGRIA" else money(rng.uniform(30, 150))
            movements.append({"type": typ, "amount": amount, "reason": reason, "when": when})

        sessions[d] = {
            "idx": idx,
            "operator": operator,
            "closed_by": closed_by,
            "opened_at": opened_at,
            "closed_at": closed_at,
            "opening_amount": money(rng.uniform(150, 400)),
            "movements": movements,
            "order_net_total": 0.0,
        }
    return sessions


def build_order_schedule(rng, operating_days):
    weights = [day_weight(d) for d in operating_days]
    sampled_days = rng.choices(operating_days, weights=weights, k=config.N_ORDERS)
    shells = []
    for d in sampled_days:
        hour = rng.choices(range(24), weights=HOUR_WEIGHTS)[0]
        ts = datetime(d.year, d.month, d.day, hour, rng.randint(0, 59), rng.randint(0, 59))
        channel = "BALCAO" if rng.random() < config.BALCAO_SHARE else "MARKETPLACE"
        shells.append({"day": d, "ts": ts, "channel": channel})
    shells.sort(key=lambda s: s["ts"])
    return shells


def pick_item(rng, products, kits, sim, warehouse):
    for _ in range(6):
        if kits and rng.random() < 0.12:
            k = rng.choice(kits)
            qty = rng.choices([1, 2], weights=[85, 15])[0]
            if all(sim.available(c["sku"], warehouse) >= c["quantity"] * qty for c in k["components"]):
                return {
                    "sku": k["sku"], "name": k["name"], "kind": "KIT", "unit_price": k["sale_price"],
                    "cost_price": k["cost_price"], "quantity": qty, "components": k["components"],
                }
        else:
            p = rng.choice(products)
            qty = rng.choices([1, 2, 3], weights=[60, 30, 10])[0]
            if sim.available(p["sku"], warehouse) >= qty:
                cost = sim.cost_at(p["sku"], warehouse)
                return {
                    "sku": p["sku"], "name": p["name"], "kind": "SIMPLES", "unit_price": p["sale_price"],
                    "cost_price": cost if cost is not None else p["cost_price"], "quantity": qty,
                }
    return None


def commit_stock_saida(sim, item, warehouse, ts, channel_label, movements):
    if item["kind"] == "KIT":
        for comp in item["components"]:
            qty = comp["quantity"] * item["quantity"]
            sim.apply_saida(comp["sku"], warehouse, qty)
            movements.append(
                {"sku": comp["sku"], "warehouse": warehouse, "type": "SAIDA", "quantity": qty,
                 "reason": f"Venda {channel_label} - baixa de kit {item['sku']}", "when": ts, "unit_cost": None}
            )
    else:
        sim.apply_saida(item["sku"], warehouse, item["quantity"])
        movements.append(
            {"sku": item["sku"], "warehouse": warehouse, "type": "SAIDA", "quantity": item["quantity"],
             "reason": f"Venda {channel_label}", "when": ts, "unit_cost": None}
        )


def build_items(rng, products, kits, sim, warehouse, ts, channel_label, movements):
    n_items = rng.choices([1, 2, 3, 4, 5], weights=[35, 30, 20, 10, 5])[0]
    items = []
    for _ in range(n_items):
        it = pick_item(rng, products, kits, sim, warehouse)
        if it is None:
            continue
        commit_stock_saida(sim, it, warehouse, ts, channel_label, movements)
        items.append(it)
    return items


def apply_discount(rng, items):
    if rng.random() >= 0.15 or not items:
        return 0.0
    target = rng.choice(items)
    line_total = target["unit_price"] * target["quantity"]
    discount = float(money(line_total * rng.uniform(0.05, 0.15)))
    target["discount_amount"] = discount
    return discount


def pick_customer(rng, customers, must_have=False):
    pool = customers
    if must_have:
        weighted = [c for c in pool if c["estagio"] in ("QUALIFICADO", "CLIENTE_ATIVO")]
        pool = weighted if rng.random() < 0.6 and weighted else pool
    return rng.choice(pool)


def build_balcao_order(rng, shell, sessions, customers, products, kits, sim, operators, movements):
    day = shell["day"]
    sess = sessions[day]
    ts = max(shell["ts"], sess["opened_at"] + timedelta(minutes=1))
    ts = min(ts, sess["closed_at"] - timedelta(minutes=1))
    warehouse = config.WAREHOUSE_LOJA

    is_comanda = rng.random() < config.COMANDA_SHARE_OF_BALCAO
    items = build_items(rng, products, kits, sim, warehouse, ts, "balcão" if not is_comanda else "comanda", movements)
    if not items:
        return None
    discount = apply_discount(rng, items)

    customer = pick_customer(rng, customers, must_have=False) if rng.random() < 0.25 else None

    outcome = rng.random()
    if outcome < config.REEMBOLSADO_SHARE_OF_BALCAO:
        status = "REEMBOLSADO"
    elif outcome < config.REEMBOLSADO_SHARE_OF_BALCAO + config.RESERVADO_SHARE_OF_BALCAO:
        status = "RESERVADO"
    else:
        status = "CONCLUIDO"

    if status == "RESERVADO" and (config.TODAY - ts).days > 5 and rng.random() < 0.7:
        status = "RESERVADO_THEN_CONCLUIDO"

    total = sum(i["unit_price"] * i["quantity"] for i in items)
    net = total - discount
    method = rng.choice(PAYMENT_METHODS_BALCAO)
    installments = rng.choices([1, 2, 3, 6, 10], weights=[50, 20, 15, 10, 5])[0] if method == "CREDITO" else None
    change_amount = float(money(rng.uniform(0, 8))) if method == "DINHEIRO" and rng.random() < 0.4 else 0.0

    order = {
        "channel": "BALCAO", "warehouse_code": warehouse, "sold_at": ts, "total_amount": float(money(total)),
        "discount_amount": discount, "net_amount": float(money(net)), "change_amount": change_amount,
        "customer": customer, "items": items, "session_day": day, "is_comanda": is_comanda,
        "comanda_label": rng.choice(TABLE_LABELS) if is_comanda else None,
        "comanda_opened_at": ts - timedelta(minutes=rng.uniform(30, 180)) if is_comanda else None,
        "operator": sess["operator"], "method": method, "installments": installments,
    }

    if status in ("CONCLUIDO", "RESERVADO_THEN_CONCLUIDO"):
        order.update({"status": "CONCLUIDO", "paid_at": cap_future(ts), "concluded_at": cap_future(ts),
                      "reserved_at": cap_future(ts) if status == "RESERVADO_THEN_CONCLUIDO" else None,
                      "cancelled_at": None, "refunded_at": None, "cancel_reason": None})
    elif status == "RESERVADO":
        order.update({"status": "RESERVADO", "paid_at": cap_future(ts), "concluded_at": None, "reserved_at": cap_future(ts),
                      "cancelled_at": None, "refunded_at": None, "cancel_reason": None})
    else:  # REEMBOLSADO
        refunded_at = cap_future(max(ts + timedelta(days=rng.uniform(1, 20)), ts + timedelta(hours=1)))
        order.update({"status": "REEMBOLSADO", "paid_at": cap_future(ts), "concluded_at": cap_future(ts), "reserved_at": None,
                      "cancelled_at": None, "refunded_at": refunded_at, "cancel_reason": rng.choice(REFUND_REASONS)})

    sess["order_net_total"] += order["net_amount"] if order["status"] != "CANCELADO" else 0.0
    return order


def build_marketplace_order(rng, shell, customers, products, kits, sim, movements):
    ts = shell["ts"]
    warehouse = config.WAREHOUSE_ECOM
    items = build_items(rng, products, kits, sim, warehouse, ts, "marketplace", movements)
    if not items:
        return None
    discount = apply_discount(rng, items)
    customer = pick_customer(rng, customers, must_have=True)

    total = sum(i["unit_price"] * i["quantity"] for i in items)
    net = total - discount
    is_recent = (config.TODAY - ts).days <= config.MARKETPLACE_RECENT_DAYS

    if is_recent:
        status = rng.choices(
            ["CANCELADO", "CRIADO", "AGUARDANDO_PAGAMENTO", "PAGO", "SEPARADO", "ENVIADO", "ENTREGUE", "CONCLUIDO"],
            weights=[10, 5, 5, 10, 15, 15, 15, 25],
        )[0]
    else:
        status = rng.choices(["CANCELADO", "REEMBOLSADO", "CONCLUIDO"], weights=[15, 10, 75])[0]

    method = rng.choice(PAYMENT_METHODS_MKT)
    installments = rng.choices([1, 2, 3, 6, 10], weights=[50, 20, 15, 10, 5])[0] if method == "CREDITO" else None
    order = {
        "channel": "MARKETPLACE", "warehouse_code": warehouse, "sold_at": ts, "total_amount": float(money(total)),
        "discount_amount": discount, "net_amount": float(money(net)), "change_amount": 0.0,
        "customer": customer, "items": items, "session_day": None, "is_comanda": False,
        "method": method, "installments": installments,
    }

    if status == "CANCELADO":
        order.update({"status": "CANCELADO", "paid_at": None, "concluded_at": None, "reserved_at": None,
                      "cancelled_at": cap_future(ts + timedelta(minutes=rng.uniform(10, 600))), "refunded_at": None,
                      "cancel_reason": rng.choice(CANCEL_REASONS), "separated_at": None, "shipped_at": None, "delivered_at": None})
        return order

    if status in ("CRIADO", "AGUARDANDO_PAGAMENTO"):
        # Ainda não pagou — esteira nem começou.
        order.update({"status": status, "paid_at": None, "concluded_at": None, "reserved_at": None,
                      "cancelled_at": None, "refunded_at": None, "cancel_reason": None,
                      "separated_at": None, "shipped_at": None, "delivered_at": None})
        return order

    paid_at = cap_future(ts + timedelta(minutes=rng.uniform(5, 120)))
    separated_at = shipped_at = delivered_at = concluded_at = None
    stage_order = ["PAGO", "SEPARADO", "ENVIADO", "ENTREGUE", "CONCLUIDO"]
    reach = stage_order.index(status) if status in stage_order else 0
    if status == "REEMBOLSADO":
        reach = len(stage_order) - 1

    if reach >= 1:
        separated_at = cap_future(paid_at + timedelta(hours=rng.uniform(1, 24)))
    if reach >= 2:
        shipped_at = cap_future(separated_at + timedelta(hours=rng.uniform(2, 48)))
    if reach >= 3:
        delivered_at = cap_future(shipped_at + timedelta(hours=rng.uniform(24, 96)))
    if reach >= 4:
        concluded_at = cap_future(delivered_at + timedelta(hours=rng.uniform(1, 48)))

    refunded_at = None
    cancel_reason = None
    final_status = status
    if status == "REEMBOLSADO":
        refunded_at = cap_future(max(concluded_at + timedelta(days=rng.uniform(1, 15)), concluded_at + timedelta(hours=1)))
        cancel_reason = rng.choice(REFUND_REASONS)

    order.update({
        "status": final_status, "paid_at": paid_at, "concluded_at": concluded_at, "reserved_at": None,
        "cancelled_at": None, "refunded_at": refunded_at, "cancel_reason": cancel_reason,
        "separated_at": separated_at, "shipped_at": shipped_at, "delivered_at": delivered_at,
    })
    return order


def emit_sessions_sql(statements, sessions):
    statements.append("-- Sessões de caixa (uma por depósito LOJA-01 por dia de operação)")
    for d in sorted(sessions.keys()):
        s = sessions[d]
        expected = float(s["opening_amount"]) + s["order_net_total"]
        for m in s["movements"]:
            expected += float(m["amount"]) if m["type"] == "SUPRIMENTO" else -float(m["amount"])
        # ck_cash_register_session_amounts exige counted_amount >= 0 — clampa aqui em vez de nos
        # valores de sangria individuais, que não conhecem o total de vendas do dia com antecedência.
        expected = max(expected, 0.0)
        counted = expected
        if rng_global.random() < 0.08:
            counted = max(expected + rng_global.uniform(-15, 15), 0.0)
        difference = counted - expected

        statements.append(
            "INSERT INTO cash_register_session (operator, opened_at, opening_amount, closed_at, status, "
            "warehouse_code, closed_by, expected_amount, counted_amount, difference_amount) VALUES ("
            f"{sqs(s['operator'])}, {sqts(s['opened_at'])}, {sqn(float(s['opening_amount']))}, "
            f"{sqts(s['closed_at'])}, 'CLOSED', {sqs(config.WAREHOUSE_LOJA)}, {sqs(s['closed_by'])}, "
            f"{sqn(round(expected, 2))}, {sqn(round(counted, 2))}, {sqn(round(difference, 2))}) "
            f"RETURNING id AS s{s['idx']} \\gset"
        )
        for m in s["movements"]:
            statements.append(
                "INSERT INTO cash_movement (session_id, type, amount, reason, username, created_at) VALUES ("
                f":s{s['idx']}, {sqs(m['type'])}, {sqn(float(m['amount']))}, {sqs(m['reason'])}, "
                f"{sqs(s['operator'])}, {sqts(m['when'])});"
            )
    statements.append("")


def emit_order_sql(statements, order, sessions, sim):
    session_ref = f":s{sessions[order['session_day']]['idx']}" if order["channel"] == "BALCAO" else "NULL"
    cust_ref = customer_ref(order["customer"])
    has_cashback_customer = order["customer"] is not None and order["customer"]["has_cpf"]
    cashback_active = has_cashback_customer and order["status"] not in ("CANCELADO",)

    comanda_id_var = None
    if order["is_comanda"]:
        statements.append(
            "INSERT INTO comanda (session_id, warehouse_code, table_or_customer_label, status, order_id, "
            "opened_by, opened_at, closed_at) VALUES ("
            f"{session_ref}, {sqs(order['warehouse_code'])}, {sqs(order['comanda_label'])}, 'ABERTA', NULL, "
            f"{sqs(order['operator'])}, {sqts(order['comanda_opened_at'])}, NULL) RETURNING id AS comanda_id \\gset"
        )
        comanda_id_var = ":comanda_id"
        n = len(order["items"])
        for i, it in enumerate(order["items"]):
            added_at = order["comanda_opened_at"] + (order["sold_at"] - order["comanda_opened_at"]) * ((i + 1) / (n + 1))
            statements.append(
                "INSERT INTO comanda_item (comanda_id, sku, quantity, unit_price, cost_price, product_name, added_at) "
                f"VALUES (:comanda_id, {sqs(it['sku'])}, {sqn(it['quantity'])}, {sqn(it['unit_price'])}, "
                f"{sqn(it['cost_price'])}, {sqs(it['name'])}, {sqts(added_at)});"
            )

    statements.append(
        "INSERT INTO sales_order (session_id, warehouse_code, sold_at, total_amount, channel, status, "
        "order_number, customer_id, discount_amount, cashback_redeemed, net_amount, change_amount, "
        "cancel_reason, paid_at, concluded_at, cancelled_at, refunded_at, reserved_at, separated_at, "
        "shipped_at, delivered_at, version) VALUES ("
        f"{session_ref}, {sqs(order['warehouse_code'])}, {sqts(order['sold_at'])}, {sqn(order['total_amount'])}, "
        f"{sqs(order['channel'])}, {sqs(order['status'])}, (SELECT nextval('order_number_seq')::text), "
        f"{cust_ref}, {sqn(order['discount_amount'])}, 0, {sqn(order['net_amount'])}, {sqn(order['change_amount'])}, "
        f"{sqs(order.get('cancel_reason'))}, {sqts(order.get('paid_at'))}, {sqts(order.get('concluded_at'))}, "
        f"{sqts(order.get('cancelled_at'))}, {sqts(order.get('refunded_at'))}, {sqts(order.get('reserved_at'))}, "
        f"{sqts(order.get('separated_at'))}, {sqts(order.get('shipped_at'))}, {sqts(order.get('delivered_at'))}, 0) "
        "RETURNING id AS order_id \\gset"
    )

    if order["is_comanda"]:
        statements.append(
            f"UPDATE comanda SET status = 'FECHADA', closed_at = {sqts(order['sold_at'])}, order_id = :order_id "
            f"WHERE id = {comanda_id_var};"
        )

    cashback_pct = config.CASHBACK_PERCENT if order["customer"] is not None else "NULL"
    for it in order["items"]:
        statements.append(
            "INSERT INTO order_item (order_id, sku, quantity, unit_price, cost_price, discount_amount, "
            "cashback_percent, product_name) VALUES ("
            f":order_id, {sqs(it['sku'])}, {sqn(it['quantity'])}, {sqn(it['unit_price'])}, {sqn(it['cost_price'])}, "
            f"{sqn(it.get('discount_amount', 0.0))}, {cashback_pct}, {sqs(it['name'])}) "
            "RETURNING id AS oi_id \\gset"
        )
        if cashback_active:
            line_amount = it["unit_price"] * it["quantity"] - it.get("discount_amount", 0.0)
            cb_amount = float(money(line_amount * float(config.CASHBACK_PERCENT) / 100))
            if cb_amount > 0:
                created_at = order["sold_at"]
                available_at = created_at + timedelta(days=config.CASHBACK_CARENCIA_DIAS)
                expires_at = created_at + timedelta(days=config.CASHBACK_EXPIRACAO_DIAS)
                statements.append(
                    "INSERT INTO cashback_entry (customer_id, order_id, order_item_id, type, amount, "
                    "available_at, expires_at, created_at) VALUES ("
                    f"{cust_ref}, :order_id, :oi_id, 'EARNED', {sqn(cb_amount)}, {sqts(available_at)}, "
                    f"{sqts(expires_at)}, {sqts(created_at)}) RETURNING id AS earned_id \\gset"
                )
                if order["status"] == "REEMBOLSADO":
                    statements.append(
                        "INSERT INTO cashback_entry (customer_id, order_id, order_item_id, type, amount, "
                        "available_at, expires_at, created_at, reverses_entry_id) VALUES ("
                        f"{cust_ref}, :order_id, :oi_id, 'REVERSED', {sqn(-cb_amount)}, {sqts(order['refunded_at'])}, "
                        f"NULL, {sqts(order['refunded_at'])}, :earned_id);"
                    )

    if order.get("paid_at") is not None:
        captured_at = order["paid_at"]
        statements.append(
            "INSERT INTO order_payment (order_id, method, amount, status, installments, captured_at, created_at) "
            "VALUES (:order_id, "
            f"{sqs(order['method'])}, {sqn(order['net_amount'])}, 'CAPTURED', "
            f"{sqn(order.get('installments'))}, {sqts(captured_at)}, {sqts(captured_at)});"
        )

    if order["status"] == "REEMBOLSADO":
        wh = order["warehouse_code"]
        for it in order["items"]:
            if it["kind"] == "KIT":
                for comp in it["components"]:
                    qty = comp["quantity"] * it["quantity"]
                    sim.apply_entrada(comp["sku"], wh, qty, None)
                    statements.append(
                        "INSERT INTO stock_movement (sku, warehouse_id, type, quantity, reason, username, created_at) "
                        f"SELECT {sqs(comp['sku'])}, w.id, 'ENTRADA', {qty}, "
                        "'Estorno de reembolso - mercadoria devolvida ao estoque', 'demo.sistema', "
                        f"{sqts(order['refunded_at'])} FROM warehouse w WHERE w.code = {sqs(wh)};"
                    )
            else:
                sim.apply_entrada(it["sku"], wh, it["quantity"], None)
                statements.append(
                    "INSERT INTO stock_movement (sku, warehouse_id, type, quantity, reason, username, created_at) "
                    f"SELECT {sqs(it['sku'])}, w.id, 'ENTRADA', {it['quantity']}, "
                    "'Estorno de reembolso - mercadoria devolvida ao estoque', 'demo.sistema', "
                    f"{sqts(order['refunded_at'])} FROM warehouse w WHERE w.code = {sqs(wh)};"
                )
    statements.append("")


def main():
    global rng_global
    rng = random.Random(config.RNG_SEED + 3)
    rng_global = rng

    catalogo = read_json(config.out_path("catalogo.json"))
    clientes = read_json(config.out_path("clientes.json"))
    compras = read_json(config.out_path("compras.json"))
    operators = read_json(config.out_path("usuarios.json"))

    for r in compras["receipts"]:
        r["received_at"] = datetime.fromisoformat(r["received_at"])
    for c in clientes:
        c["cadastrado_em"] = datetime.fromisoformat(c["cadastrado_em"])

    products = catalogo["products"]
    kits = catalogo["kits"]

    operating_days = build_calendar(rng)
    sessions = build_sessions(rng, operating_days, operators)
    schedule = build_order_schedule(rng, operating_days)

    receipts_sorted = sorted(compras["receipts"], key=lambda r: r["received_at"])
    ptr = 0
    sim = StockSim()
    movements = []
    built_orders = []

    for shell in schedule:
        while ptr < len(receipts_sorted) and receipts_sorted[ptr]["received_at"] <= shell["ts"]:
            r = receipts_sorted[ptr]
            sim.apply_entrada(r["sku"], r["warehouse_code"], r["quantity"], r["unit_cost"])
            ptr += 1

        if shell["channel"] == "BALCAO":
            order = build_balcao_order(rng, shell, sessions, clientes, products, kits, sim, operators, movements)
        else:
            order = build_marketplace_order(rng, shell, clientes, products, kits, sim, movements)
        if order:
            built_orders.append(order)

    while ptr < len(receipts_sorted):
        r = receipts_sorted[ptr]
        sim.apply_entrada(r["sku"], r["warehouse_code"], r["quantity"], r["unit_cost"])
        ptr += 1

    statements = ["-- Fase 07: vendas (PDV/caixa/comanda + pedidos de marketplace)", ""]
    emit_sessions_sql(statements, sessions)

    statements.append("-- Pedidos, em ordem cronológica")
    for order in built_orders:
        emit_order_sql(statements, order, sessions, sim)

    statements.append("-- Movimentos de estoque de SAÍDA (baixa por venda, kit já explodido em componentes)")
    for m in movements:
        statements.append(
            "INSERT INTO stock_movement (sku, warehouse_id, type, quantity, reason, username, created_at, unit_cost) "
            f"SELECT {sqs(m['sku'])}, w.id, {sqs(m['type'])}, {m['quantity']}, {sqs(m['reason'])}, "
            f"'demo.sistema', {sqts(m['when'])}, {sqn(m['unit_cost'])} "
            f"FROM warehouse w WHERE w.code = {sqs(m['warehouse'])};"
        )
    statements.append("")

    statements.append("-- Saldo final de estoque consolidado (entradas + saídas simuladas em conjunto)")
    for (sku, wh), qty in sim.balance.items():
        # Toda chave aqui teve pelo menos uma ENTRADA real (é o único jeito de existir no dict) —
        # sempre grava a linha, mesmo com quantity=0 (item esgotado), igual à aplicação real.
        avg = sim.avg_cost.get((sku, wh))
        statements.append(
            "INSERT INTO stock_balance (sku, warehouse_id, quantity, reserved_quantity, version, average_cost) "
            f"SELECT {sqs(sku)}, w.id, {sqn(round(qty, 3))}, 0, 0, {sqn(round(avg, 2)) if avg is not None else 'NULL'} "
            f"FROM warehouse w WHERE w.code = {sqs(wh)} "
            f"ON CONFLICT (sku, warehouse_id) DO UPDATE SET quantity = EXCLUDED.quantity, "
            "average_cost = EXCLUDED.average_cost;"
        )
    statements.append("")

    write_sql(config.out_path("07_vendas.sql"), statements)

    # Resumo enxuto (sem PII, sem IDs de banco — não conhecidos em tempo de geração) para a fase
    # financeira agregar receita por mês/canal sem duplicar a simulação de vendas.
    write_json(
        config.out_path("vendas_resumo.json"),
        [
            {"sold_at": o["sold_at"], "channel": o["channel"], "net_amount": o["net_amount"], "status": o["status"]}
            for o in built_orders
        ],
    )

    n_balcao = sum(1 for o in built_orders if o["channel"] == "BALCAO")
    n_mkt = len(built_orders) - n_balcao
    print(f"07_vendas: {len(built_orders)} pedidos ({n_balcao} balcão, {n_mkt} marketplace), "
          f"{len(sessions)} sessões de caixa, {len(movements)} baixas de estoque.")


if __name__ == "__main__":
    main()
