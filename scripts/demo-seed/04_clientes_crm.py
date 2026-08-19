"""Fase 04 — Clientes CRM: ~500 clientes em estágios variados, com histórico de transição de
estágio, tags, notas de atendimento e automações de campanha (+ log de disparo).
"""
import random
import sys
from datetime import timedelta

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import daterange_random, gen_cpf, gen_phone, read_json, sqb, sqn, sqs, sqts, write_json, write_sql
from names import FIRST_NAMES, LAST_NAMES

STAGE_ORDER = ["NOVO_LEAD", "EM_ATENDIMENTO", "QUALIFICADO", "CLIENTE_ATIVO"]

STAGE_TARGET_COUNTS = {
    "NOVO_LEAD": 125,
    "EM_ATENDIMENTO": 75,
    "QUALIFICADO": 75,
    "CLIENTE_ATIVO": 175,
    "INATIVO": 50,
}

ORIGENS = ["Instagram", "Indicação", "Loja física", "Google", "WhatsApp", "Facebook", "Marketplace"]

TAGS = [
    "VIP", "Atacado", "Fidelidade", "Reclamação", "Indicador",
    "Aniversariante do Mês", "Sem Contato Recente", "Primeira Compra",
]

NOTE_TEMPLATES = [
    "Cliente interessado em narguilé novo, ligar semana que vem.",
    "Perguntou sobre kit de sedas premium, aguardando reposição.",
    "Reclamou de atraso na entrega, resolvido com desconto na próxima compra.",
    "Comprou para presente, pediu embalagem especial.",
    "Perguntou sobre programa de cashback e como resgatar.",
    "Cliente fiel, sempre compra carvão e essência juntos.",
    "Pediu indicação de essência para narguilé iniciante.",
    "Elogiou o atendimento na loja física.",
    "Interessado em revenda no atacado, encaminhado para o gerente.",
    "Não retornou contato nas últimas duas tentativas.",
    "Aniversário no próximo mês — bom gatilho pra campanha.",
    "Perguntou sobre disponibilidade de charutos importados.",
]

STAGE_DAY_RANGE = {
    "NOVO_LEAD": (config.WINDOW_DAYS - 60, config.WINDOW_DAYS - 1),
    "EM_ATENDIMENTO": (config.WINDOW_DAYS - 150, config.WINDOW_DAYS - 10),
    "QUALIFICADO": (config.WINDOW_DAYS - 240, config.WINDOW_DAYS - 30),
    "CLIENTE_ATIVO": (10, config.WINDOW_DAYS - 45),
    "INATIVO": (10, config.WINDOW_DAYS - 200),
}


def strip_accents(s):
    import unicodedata

    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


def build_stage_plan(rng):
    plan = []
    for stage, count in STAGE_TARGET_COUNTS.items():
        plan += [stage] * count
    rng.shuffle(plan)
    return plan


def registered_at(rng, stage):
    lo, hi = STAGE_DAY_RANGE[stage]
    lo, hi = min(lo, hi), max(lo, hi)
    offset_days = rng.uniform(max(lo, 0), max(hi, lo + 1))
    return config.START_DATE + timedelta(days=offset_days, hours=rng.uniform(8, 21))


def build_identifiers(rng, used_emails, used_cpfs, used_phones, first, last, idx):
    scenario = rng.choices(
        ["all3", "cpf_contato", "email_only", "contato_only", "cpf_only"],
        weights=[55, 20, 10, 10, 5],
    )[0]
    email = None
    cpf = None
    contato = None

    base = f"{strip_accents(first).lower()}.{strip_accents(last).lower()}"

    def uniq_email():
        e = f"{base}{idx}@{config.DEMO_EMAIL_DOMAIN}"
        while e in used_emails:
            idx_local = rng.randint(1000, 999999)
            e = f"{base}{idx_local}@{config.DEMO_EMAIL_DOMAIN}"
        used_emails.add(e)
        return e

    def uniq_cpf():
        c = gen_cpf(rng)
        while c in used_cpfs:
            c = gen_cpf(rng)
        used_cpfs.add(c)
        return c

    def uniq_phone():
        p = gen_phone(rng)
        while p in used_phones:
            p = gen_phone(rng)
        used_phones.add(p)
        return p

    if scenario == "all3":
        email, cpf, contato = uniq_email(), uniq_cpf(), uniq_phone()
    elif scenario == "cpf_contato":
        cpf, contato = uniq_cpf(), uniq_phone()
    elif scenario == "email_only":
        email = uniq_email()
    elif scenario == "contato_only":
        contato = uniq_phone()
    elif scenario == "cpf_only":
        cpf = uniq_cpf()

    if email:
        lookup_col, lookup_val = "email", email
    elif cpf:
        lookup_col, lookup_val = "cpf", cpf
    else:
        lookup_col, lookup_val = "contato", contato

    return email, cpf, contato, lookup_col, lookup_val


def build_transitions(rng, stage, cadastrado_em, operators):
    """Retorna lista de (de, para, transicionado_em) coerente com o estágio final."""
    transitions = []
    if stage == "INATIVO":
        peak = rng.choices(["QUALIFICADO", "CLIENTE_ATIVO"], weights=[30, 70])[0]
        path = STAGE_ORDER[: STAGE_ORDER.index(peak) + 1]
    elif stage in STAGE_ORDER:
        path = STAGE_ORDER[: STAGE_ORDER.index(stage) + 1]
    else:
        path = ["NOVO_LEAD"]

    if len(path) <= 1 and stage != "INATIVO":
        return transitions

    max_end = config.TODAY - timedelta(days=1)
    span = max(int((max_end - cadastrado_em).days), 1)
    n_steps = len(path) - 1 + (1 if stage == "INATIVO" else 0)
    if n_steps <= 0:
        return transitions

    checkpoints = sorted(rng.uniform(0, span) for _ in range(n_steps))
    ts_list = [cadastrado_em + timedelta(days=d) for d in checkpoints]

    step = 0
    for i in range(len(path) - 1):
        transitions.append((path[i], path[i + 1], ts_list[step]))
        step += 1
    if stage == "INATIVO":
        transitions.append((path[-1], "INATIVO", ts_list[step]))

    return transitions


def main():
    rng = random.Random(config.RNG_SEED + 1)
    operators = read_json(config.out_path("usuarios.json"))
    author_pool = [o["username"] for o in operators if o["role"] in ("ROLE_ATENDENTE", "ROLE_ADMIN")] or [
        o["username"] for o in operators
    ]

    stage_plan = build_stage_plan(rng)
    used_emails, used_cpfs, used_phones = set(), set(), set()

    customers = []
    for idx, stage in enumerate(stage_plan, 1):
        first = rng.choice(FIRST_NAMES)
        last = rng.choice(LAST_NAMES)
        nome = f"{first} {last}"
        cadastrado_em = registered_at(rng, stage)
        email, cpf, contato, lookup_col, lookup_val = build_identifiers(
            rng, used_emails, used_cpfs, used_phones, first, last, idx
        )
        origem = rng.choice(ORIGENS)
        transitions = build_transitions(rng, stage, cadastrado_em, author_pool)

        customers.append(
            {
                "nome": nome,
                "email": email,
                "cpf": cpf,
                "contato": contato,
                "origem": origem,
                "cadastrado_em": cadastrado_em,
                "estagio": stage,
                "lookup_col": lookup_col,
                "lookup_val": lookup_val,
                "has_cpf": cpf is not None,
                "transitions": [
                    {"de": de, "para": para, "em": em, "autor": rng.choice(author_pool)}
                    for de, para, em in transitions
                ],
            }
        )

    # ---- Tags -------------------------------------------------------------------------------
    tag_bias = {
        "VIP": "CLIENTE_ATIVO",
        "Sem Contato Recente": "INATIVO",
        "Fidelidade": "CLIENTE_ATIVO",
        "Reclamação": None,
        "Atacado": None,
        "Indicador": "CLIENTE_ATIVO",
        "Aniversariante do Mês": None,
        "Primeira Compra": "NOVO_LEAD",
    }
    for c in customers:
        if rng.random() < 0.40:
            n_tags = rng.choice([1, 1, 2])
            weighted = [t for t in TAGS if tag_bias[t] is None or tag_bias[t] == c["estagio"]]
            pool = weighted if weighted else TAGS
            c["tags"] = rng.sample(pool, k=min(n_tags, len(pool)))
        else:
            c["tags"] = []

    # ---- Notas de atendimento ------------------------------------------------------------
    for c in customers:
        weight = 0.40 if c["estagio"] in ("EM_ATENDIMENTO", "QUALIFICADO", "CLIENTE_ATIVO") else 0.10
        if rng.random() < weight:
            n_notes = rng.choice([1, 1, 2, 3])
            notes = []
            for _ in range(n_notes):
                notes.append(
                    {
                        "texto": rng.choice(NOTE_TEMPLATES),
                        "autor": rng.choice(author_pool),
                        "em": daterange_random(rng, c["cadastrado_em"], config.TODAY - timedelta(days=1)),
                    }
                )
            c["notes"] = notes
        else:
            c["notes"] = []

    # ---- Automações de campanha -------------------------------------------------------------
    automations = [
        {
            "nome": "Boas-vindas Novo Lead",
            "gatilho": "ENTRADA_ESTAGIO",
            "segmento_alvo": "NOVO_LEAD",
            "canal": "WHATSAPP",
            "template": "Oi {nome}! Bem-vindo(a) à Mahal Tabacaria 🌿 Qualquer dúvida, fala com a gente!",
            "criado_em": config.START_DATE + timedelta(days=2),
        },
        {
            "nome": "Reengajamento Qualificados",
            "gatilho": "ENTRADA_ESTAGIO",
            "segmento_alvo": "QUALIFICADO",
            "canal": "EMAIL",
            "template": "Vimos que você já conhece a Mahal — que tal fechar seu primeiro pedido com 10% off?",
            "criado_em": config.START_DATE + timedelta(days=5),
        },
        {
            "nome": "Recuperação de Inativos",
            "gatilho": "MANUAL",
            "segmento_alvo": "INATIVO",
            "canal": "AMBOS",
            "template": "Sentimos sua falta! Voltamos com novidades e um cupom especial pra você.",
            "criado_em": config.START_DATE + timedelta(days=20),
        },
        {
            "nome": "Promoção Clientes Ativos",
            "gatilho": "MANUAL",
            "segmento_alvo": "CLIENTE_ATIVO",
            "canal": "WHATSAPP",
            "template": "Cliente Mahal tem desconto exclusivo essa semana. Vem conferir!",
            "criado_em": config.START_DATE + timedelta(days=30),
        },
    ]

    campaign_logs = []  # (automation_name, customer_index, status, disparado_em, convertido_em)
    STATUS_WEIGHTS = ["ENVIADO"] * 75 + ["PENDENTE_INTEGRACAO"] * 15 + ["FALHA"] * 10

    for c_idx, c in enumerate(customers):
        for t in c["transitions"]:
            for auto in automations:
                if auto["gatilho"] == "ENTRADA_ESTAGIO" and auto["segmento_alvo"] == t["para"]:
                    if rng.random() < 0.80:
                        status = rng.choice(STATUS_WEIGHTS)
                        disparado_em = t["em"] + timedelta(minutes=rng.randint(1, 240))
                        convertido_em = None
                        if status == "ENVIADO" and rng.random() < 0.30:
                            convertido_em = disparado_em + timedelta(days=rng.randint(1, 14))
                        campaign_logs.append((auto["nome"], c_idx, status, disparado_em, convertido_em))

    for auto in automations:
        if auto["gatilho"] != "MANUAL":
            continue
        candidates = [i for i, c in enumerate(customers) if c["estagio"] == auto["segmento_alvo"]]
        chosen = rng.sample(candidates, k=max(1, int(len(candidates) * 0.30))) if candidates else []
        for c_idx in chosen:
            status = rng.choice(STATUS_WEIGHTS)
            disparado_em = config.TODAY - timedelta(days=rng.uniform(1, 60))
            convertido_em = disparado_em + timedelta(days=rng.randint(1, 10)) if status == "ENVIADO" and rng.random() < 0.30 else None
            campaign_logs.append((auto["nome"], c_idx, status, disparado_em, convertido_em))

    # ---- Emissão de SQL -----------------------------------------------------------------
    statements = ["-- Fase 04: clientes CRM (cadastro, tags, notas, estágios, automações)", ""]

    for t in TAGS:
        statements.append(f"INSERT INTO tags (nome) VALUES ({sqs(t)}) ON CONFLICT (nome) DO NOTHING;")
    statements.append("")

    for c in customers:
        statements.append(
            "INSERT INTO customers (nome, contato, email, cpf, origem, cadastrado_em, estagio) VALUES ("
            f"{sqs(c['nome'])}, {sqs(c['contato'])}, {sqs(c['email'])}, {sqs(c['cpf'])}, "
            f"{sqs(c['origem'])}, {sqts(c['cadastrado_em'])}, {sqs(c['estagio'])});"
        )

    def customer_ref_subquery(c):
        return f"(SELECT id FROM customers WHERE {c['lookup_col']} = {sqs(c['lookup_val'])})"

    statements.append("")
    for c in customers:
        ref = customer_ref_subquery(c)
        for tr in c["transitions"]:
            statements.append(
                "INSERT INTO customer_stage_transitions (customer_id, de, para, autor, transicionado_em) VALUES ("
                f"{ref}, {sqs(tr['de'])}, {sqs(tr['para'])}, {sqs(tr['autor'])}, {sqts(tr['em'])});"
            )

    statements.append("")
    for c in customers:
        if not c["tags"]:
            continue
        ref = customer_ref_subquery(c)
        for tag in c["tags"]:
            statements.append(
                "INSERT INTO customer_tags (customer_id, tag_id) "
                f"SELECT {ref}, t.id FROM tags t WHERE t.nome = {sqs(tag)};"
            )

    statements.append("")
    for c in customers:
        if not c["notes"]:
            continue
        ref = customer_ref_subquery(c)
        for note in c["notes"]:
            statements.append(
                "INSERT INTO customer_notes (customer_id, autor, texto, criado_em) VALUES ("
                f"{ref}, {sqs(note['autor'])}, {sqs(note['texto'])}, {sqts(note['em'])});"
            )

    statements.append("")
    for auto in automations:
        statements.append(
            "INSERT INTO campaign_automations (nome, gatilho, segmento_alvo, canal, template, ativa, criado_em) "
            "VALUES ("
            f"{sqs(auto['nome'])}, {sqs(auto['gatilho'])}, {sqs(auto['segmento_alvo'])}, {sqs(auto['canal'])}, "
            f"{sqs(auto['template'])}, TRUE, {sqts(auto['criado_em'])});"
        )

    statements.append("")
    for auto_name, c_idx, status, disparado_em, convertido_em in campaign_logs:
        ref = customer_ref_subquery(customers[c_idx])
        statements.append(
            "INSERT INTO campaign_log (automation_id, customer_id, status, disparado_em, convertido_em) "
            "SELECT a.id, "
            f"{ref}, {sqs(status)}, {sqts(disparado_em)}, {sqts(convertido_em)} "
            f"FROM campaign_automations a WHERE a.nome = {sqs(auto_name)};"
        )
    statements.append("")

    write_sql(config.out_path("04_clientes_crm.sql"), statements)

    # JSON enxuto para as fases seguintes (só o necessário para vendas/cashback).
    slim_customers = [
        {
            "nome": c["nome"],
            "estagio": c["estagio"],
            "lookup_col": c["lookup_col"],
            "lookup_val": c["lookup_val"],
            "has_cpf": c["has_cpf"],
            "cadastrado_em": c["cadastrado_em"],
        }
        for c in customers
    ]
    write_json(config.out_path("clientes.json"), slim_customers)

    n_with_cpf = sum(1 for c in customers if c["has_cpf"])
    print(f"04_clientes_crm: {len(customers)} clientes ({n_with_cpf} com CPF), {len(campaign_logs)} disparos de campanha.")


if __name__ == "__main__":
    main()
