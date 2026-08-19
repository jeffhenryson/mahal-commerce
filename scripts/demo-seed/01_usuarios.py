"""Fase 01 — Usuários/acessos: ~25 operadores com papéis variados.

Roles/permissions já existem em hml via migration (ROLE_ADMIN, ROLE_ATENDENTE, ROLE_USER,
ROLE_DEV, ROLE_CUSTOMER) — este script só cria as CONTAS de operador e o vínculo user_roles.
Todos usam a mesma senha de demonstração (config.DEMO_PASSWORD), com hash bcrypt gerado agora.
"""
import random
import sys
import unicodedata

sys.path.insert(0, str(__file__.rsplit("/", 1)[0]))
import config
from common import bcrypt_hash, daterange_random, sqb, sqs, sqts, write_json, write_sql
from names import FIRST_NAMES, LAST_NAMES


def strip_accents(s: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


def build_operators(rng: random.Random):
    role_plan = (
        ["ROLE_ADMIN"] * 2
        + ["ROLE_ATENDENTE"] * 18
        + ["ROLE_USER"] * 4
        + ["ROLE_DEV"] * 1
    )
    assert len(role_plan) == config.N_OPERATORS
    rng.shuffle(role_plan)

    used_usernames = set()
    used_emails = set()
    operators = []
    first_pool = list(FIRST_NAMES)
    last_pool = list(LAST_NAMES)
    rng.shuffle(first_pool)
    rng.shuffle(last_pool)

    for i, role in enumerate(role_plan):
        first = first_pool[i % len(first_pool)]
        last = last_pool[(i * 3) % len(last_pool)]
        base_username = f"{strip_accents(first).lower()}.{strip_accents(last).lower()}"
        username = base_username
        suffix = 1
        while username in used_usernames:
            suffix += 1
            username = f"{base_username}{suffix}"
        used_usernames.add(username)

        full_username = f"{config.DEMO_USER_PREFIX}{username}"
        email = f"{username}@{config.DEMO_EMAIL_DOMAIN}"
        while email in used_emails:
            suffix += 1
            email = f"{username}{suffix}@{config.DEMO_EMAIL_DOMAIN}"
        used_emails.add(email)

        # Time viu formado ao longo dos primeiros ~60% da janela, com reforços mais recentes.
        created_at = daterange_random(
            rng, config.START_DATE, config.START_DATE + (config.TODAY - config.START_DATE) * 0.6
        )

        operators.append(
            {
                "username": full_username,
                "display_name": f"{first} {last}",
                "email": email,
                "role": role,
                "created_at": created_at,
            }
        )
    return operators


def main():
    rng = random.Random(config.RNG_SEED)
    operators = build_operators(rng)
    password_hash = bcrypt_hash(config.DEMO_PASSWORD)
    admin_password_hash = bcrypt_hash(config.ADMIN_PASSWORD)

    admin = {
        "username": config.ADMIN_USERNAME,
        "display_name": "Administrador",
        "email": config.ADMIN_EMAIL,
        "role": "ROLE_ADMIN",
        "created_at": config.START_DATE,
    }

    statements = [
        "-- Fase 01: usuários/acessos (operadores de demonstração)",
        f"-- Senha de todos os demo.*: {config.DEMO_PASSWORD}",
        f"-- Login de vitrine: {config.ADMIN_USERNAME} / {config.ADMIN_PASSWORD}",
        "",
        "-- Conta de administrador com nome fixo — ON CONFLICT DO UPDATE para sempre sincronizar "
        "a senha, mesmo que a conta já exista de uma rodada anterior.",
        "INSERT INTO users (username, password, enabled, email, email_verified, auth_provider, "
        "user_type, created_at) VALUES ("
        f"{sqs(admin['username'])}, {sqs(admin_password_hash)}, TRUE, {sqs(admin['email'])}, TRUE, "
        f"'LOCAL', 'OPERATOR', {sqts(admin['created_at'])}"
        ") ON CONFLICT (user_type, username) DO UPDATE SET "
        "password = EXCLUDED.password, enabled = TRUE, email_verified = TRUE, deleted_at = NULL;",
        "INSERT INTO user_roles (user_id, role_id) "
        f"SELECT u.id, r.id FROM users u, roles r "
        f"WHERE u.username = {sqs(admin['username'])} AND u.user_type = 'OPERATOR' "
        "AND r.name = 'ROLE_ADMIN' ON CONFLICT DO NOTHING;",
        "",
    ]
    for op in operators:
        statements.append(
            "INSERT INTO users (username, password, enabled, email, email_verified, "
            "auth_provider, user_type, created_at) VALUES ("
            f"{sqs(op['username'])}, {sqs(password_hash)}, TRUE, {sqs(op['email'])}, TRUE, "
            f"'LOCAL', 'OPERATOR', {sqts(op['created_at'])}"
            ") ON CONFLICT (user_type, username) DO NOTHING;"
        )
        statements.append(
            "INSERT INTO user_roles (user_id, role_id) "
            f"SELECT u.id, r.id FROM users u, roles r "
            f"WHERE u.username = {sqs(op['username'])} AND u.user_type = 'OPERATOR' "
            f"AND r.name = {sqs(op['role'])} "
            "ON CONFLICT DO NOTHING;"
        )
    statements.append("")

    write_sql(config.out_path("01_usuarios.sql"), statements)
    write_json(config.out_path("usuarios.json"), [admin] + operators)
    print(f"01_usuarios: {len(operators)} operadores + 1 administrador de vitrine gerados.")


if __name__ == "__main__":
    main()
