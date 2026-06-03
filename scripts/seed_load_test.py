#!/usr/bin/env python3
"""
seed_load_test.py — Popula o servidor security-spring com dados de teste realistas.

Uso:
    # Passo 1 (só na primeira vez): criar o usuário admin no banco
    python3 scripts/seed_load_test.py --setup

    # Passo 2: rodar a simulação de carga
    python3 scripts/seed_load_test.py

    # Personalizar:
    python3 scripts/seed_load_test.py --api http://localhost:8080 --duration 120

Requer: pip install requests
"""

import argparse
import random
import subprocess
import sys
import time
from typing import Optional

try:
    import requests
    from requests import Session
except ImportError:
    print("❌  Instale requests: pip install requests")
    sys.exit(1)

# ─── Configuração padrão ─────────────────────────────────────────────────────

BASE_URL     = "http://localhost:8080"
ADMIN_USER   = "admin"
ADMIN_PASS   = "Jeff@180203"   # senha criada pelo --setup
DB_HOST      = "localhost"
DB_PORT      = "5433"
DB_NAME      = "security"
DB_USER      = "postgres"
DB_PASS      = "postgres"

# Usuários de teste criados pelo script (admin os cria diretamente, sem e-mail)
TEST_USERS = [
    {"username": f"testuser{i:02d}", "password": "Test@pass1!"}
    for i in range(1, 16)
]

# ─── Helpers HTTP ────────────────────────────────────────────────────────────

def post(session: Session, path: str, body: dict, label: str = "") -> Optional[dict]:
    try:
        r = session.post(f"{BASE_URL}{path}", json=body, timeout=8)
        if r.status_code < 500:
            return r.json() if r.content else {}
        print(f"  ⚠  {label or path} → {r.status_code}")
    except Exception as e:
        print(f"  ✗  {label or path}: {e}")
    return None

def get(session: Session, path: str, params: dict = None, label: str = "") -> Optional[dict]:
    try:
        r = session.get(f"{BASE_URL}{path}", params=params, timeout=8)
        if r.status_code < 500:
            return r.json() if r.content else {}
    except Exception as e:
        print(f"  ✗  {label or path}: {e}")
    return None

def put(session: Session, path: str, label: str = "") -> None:
    try:
        session.put(f"{BASE_URL}{path}", timeout=8)
    except Exception:
        pass

def delete(session: Session, path: str, label: str = "") -> None:
    try:
        session.delete(f"{BASE_URL}{path}", timeout=8)
    except Exception:
        pass

def bearer(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}

# ─── Auth helpers ─────────────────────────────────────────────────────────────

def login(username: str, password: str) -> Optional[str]:
    """Retorna o accessToken ou None."""
    s = requests.Session()
    data = post(s, "/auth/login", {"username": username, "password": password}, f"login:{username}")
    if data and "accessToken" in data:
        return data["accessToken"]
    return None

def login_session(username: str, password: str) -> Optional[Session]:
    """Retorna uma Session já autenticada ou None."""
    token = login(username, password)
    if not token:
        return None
    s = requests.Session()
    s.headers.update(bearer(token))
    return s

# ─── Setup: criar admin via psql ─────────────────────────────────────────────

SETUP_SQL = r"""
DO $$
DECLARE
  admin_id BIGINT;
  dev_id   BIGINT;
BEGIN
  -- Roles
  INSERT INTO roles (name) VALUES ('ROLE_ADMIN'),('ROLE_USER'),('ROLE_DEV')
    ON CONFLICT (name) DO NOTHING;

  -- Permissões
  INSERT INTO permissions (name) VALUES
    ('USER_CREATE'),('USER_READ'),('USER_UPDATE'),('USER_DELETE'),
    ('USER_ROLE_ASSIGN'),('USER_STATUS'),
    ('ROLE_READ'),('ROLE_MANAGE_PERMISSIONS'),
    ('PERMISSION_READ'),('AUDIT_READ')
  ON CONFLICT (name) DO NOTHING;

  -- ROLE_ADMIN → permissões
  INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r JOIN permissions p
      ON p.name IN ('USER_CREATE','USER_READ','USER_UPDATE','USER_DELETE',
                    'USER_ROLE_ASSIGN','USER_STATUS','ROLE_READ',
                    'ROLE_MANAGE_PERMISSIONS','PERMISSION_READ','AUDIT_READ')
    WHERE r.name = 'ROLE_ADMIN'
  ON CONFLICT DO NOTHING;

  -- Usuário admin  (bcrypt de "Admin@test1!")
  INSERT INTO users (username, password, enabled, email_verified)
    VALUES ('admin',
            '$2a$10$hbqDYz1lQlYHgkPH4g0mjeF2oHTrq4T3c2eWM9f9zNvpXN3XM7dKS',
            TRUE, TRUE)
  ON CONFLICT (username) DO NOTHING;

  SELECT id INTO admin_id FROM users WHERE username = 'admin';

  INSERT INTO user_roles (user_id, role_id)
    SELECT admin_id, r.id FROM roles r WHERE r.name = 'ROLE_ADMIN'
  ON CONFLICT DO NOTHING;

END $$;
"""

def run_setup():
    print("⚙  Criando usuário admin no banco …")
    # Gera o hash correto em Python e substitui no SQL
    try:
        import bcrypt
        hashed = bcrypt.hashpw(ADMIN_PASS.encode(), bcrypt.gensalt(10)).decode()
    except ImportError:
        print("   ℹ  bcrypt não instalado; usando hash pré-gerado (pip install bcrypt para gerar um novo)")
        hashed = "$2a$10$hbqDYz1lQlYHgkPH4g0mjeF2oHTrq4T3c2eWM9f9zNvpXN3XM7dKS"

    sql = SETUP_SQL.replace(
        "$2a$10$hbqDYz1lQlYHgkPH4g0mjeF2oHTrq4T3c2eWM9f9zNvpXN3XM7dKS",
        hashed
    )

    cmd = [
        "psql",
        "-h", DB_HOST, "-p", DB_PORT,
        "-U", DB_USER, "-d", DB_NAME,
        "-c", sql,
    ]
    env = {"PGPASSWORD": DB_PASS, "PATH": "/usr/bin:/usr/local/bin:/bin"}
    result = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if result.returncode == 0:
        print(f"✅  Admin criado — usuário: {ADMIN_USER}  senha: {ADMIN_PASS}")
    else:
        print("❌  Erro ao executar psql:")
        print(result.stderr[:500])
        sys.exit(1)


# ─── Fase 1: criar usuários de teste ─────────────────────────────────────────

def create_test_users(admin_session: Session) -> list[str]:
    """Cria usuários via API de admin (sem precisar verificar e-mail). Retorna usernames criados."""
    print("\n👥  Criando usuários de teste …")
    created = []
    for u in TEST_USERS:
        r = admin_session.post(
            f"{BASE_URL}/users",
            json={"username": u["username"], "password": u["password"]},
            timeout=8,
        )
        if r.status_code in (200, 201):
            created.append(u["username"])
            print(f"   ✅  {u['username']}")
        elif r.status_code == 409:
            created.append(u["username"])   # já existe
            print(f"   ℹ  {u['username']} já existe")
        else:
            print(f"   ⚠  {u['username']}: {r.status_code}")

    # Atribuir ROLE_USER a cada um
    for username in created:
        admin_session.post(
            f"{BASE_URL}/users/{username}/roles/ROLE_USER",
            timeout=8,
        )

    print(f"   → {len(created)} usuários prontos")
    return created


# ─── Fase 2: logins bem-sucedidos ─────────────────────────────────────────────

def simulate_logins(usernames: list[str], count: int = 60):
    print(f"\n🔑  Simulando {count} logins bem-sucedidos …")
    ok = 0
    for _ in range(count):
        u = random.choice(usernames + [ADMIN_USER])
        pwd = ADMIN_PASS if u == ADMIN_USER else TEST_USERS[int(u[-2:]) - 1]["password"]
        token = login(u, pwd)
        if token:
            ok += 1
            # Simular uma requisição autenticada antes de "sair"
            s = requests.Session()
            s.headers.update(bearer(token))
            get(s, "/users/me")
            # ~30% dos logins fazem logout explícito
            if random.random() < 0.3:
                refresh = login.__wrapped__(u, pwd) if hasattr(login, "__wrapped__") else None
        time.sleep(random.uniform(0.1, 0.4))
    print(f"   → {ok}/{count} logins com sucesso")


# ─── Fase 3: logins falhos (gera métricas de segurança) ──────────────────────

def simulate_failed_logins(usernames: list[str]):
    print("\n🚨  Simulando logins falhos (senhas erradas) …")
    s = requests.Session()
    attempts = 0
    # Tenta 3-4 vezes em cada usuário para gerar eventos sem bloquear todos
    for u in random.sample(usernames, min(6, len(usernames))):
        for _ in range(random.randint(2, 4)):
            post(s, "/auth/login",
                 {"username": u, "password": "SenhaErrada@99"},
                 f"fail-login:{u}")
            attempts += 1
            time.sleep(random.uniform(0.05, 0.2))
    print(f"   → {attempts} tentativas falhas enviadas")


# ─── Fase 4: operações variadas (latência + cobertura de rotas) ───────────────

def simulate_admin_operations(admin_session: Session):
    print("\n🛠  Simulando operações administrativas …")

    # Listar usuários com filtros variados
    for _ in range(10):
        get(admin_session, "/users", params={"page": 0, "size": 10})
        get(admin_session, "/users", params={"enabled": "true", "page": 0, "size": 5})
        time.sleep(0.1)

    # Listar roles e permissões
    for _ in range(8):
        get(admin_session, "/roles")
        get(admin_session, "/permissions")
        time.sleep(0.1)

    # Audit logs com filtros diferentes
    for action in ["USER_LOGGED_IN", "USER_LOGGED_OUT", "USER_CREATED"]:
        get(admin_session, "/audit-logs", params={"action": action, "page": 0, "size": 20})
        time.sleep(0.1)

    # Stats do dashboard
    for _ in range(5):
        get(admin_session, "/stats")
        time.sleep(0.2)

    print("   → operações concluídas")


def simulate_user_operations(usernames: list[str], rounds: int = 3):
    print(f"\n👤  Simulando operações de usuários comuns ({rounds} rounds) …")
    for round_n in range(rounds):
        for u in random.sample(usernames, min(5, len(usernames))):
            pwd = TEST_USERS[int(u[-2:]) - 1]["password"]
            s = login_session(u, pwd)
            if not s:
                continue

            # Perfil próprio
            get(s, "/users/me")

            # Listar sessões ativas
            get(s, "/auth/sessions")

            # Status 2FA
            get(s, "/auth/2fa/status")

            time.sleep(random.uniform(0.05, 0.2))

        print(f"   round {round_n + 1}/{rounds} ✓")
        time.sleep(0.5)


# ─── Fase 5: rafaga de requisições (preenche gráficos de latência/req/s) ──────

def burst_requests(admin_session: Session, duration_s: int = 30):
    print(f"\n⚡  Rafada de requisições por {duration_s}s (preenche gráficos de latência) …")
    endpoints = [
        ("/users/me", {}),
        ("/stats", {}),
        ("/roles", {}),
        ("/audit-logs", {"page": "0", "size": "5"}),
        ("/permissions", {}),
        ("/users", {"page": "0", "size": "10"}),
        ("/system/config/public", {}),
    ]
    end = time.time() + duration_s
    count = 0
    while time.time() < end:
        path, params = random.choice(endpoints)
        if params:
            get(admin_session, path, params=params)
        else:
            get(admin_session, path)
        count += 1
        time.sleep(random.uniform(0.05, 0.25))

    print(f"   → {count} requisições enviadas")


# ─── Fase 6: gera alguns erros 4xx (cobre métricas de erro) ──────────────────

def simulate_errors():
    print("\n💥  Gerando alguns erros esperados (404 / 401) …")
    s = requests.Session()
    for _ in range(8):
        try:
            s.get(f"{BASE_URL}/users/99999999", timeout=5)   # 401 ou 404
            s.get(f"{BASE_URL}/roles/role_nao_existe", timeout=5)  # 401
        except Exception:
            pass
        time.sleep(0.15)
    print("   → erros gerados")


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    global BASE_URL, ADMIN_USER, ADMIN_PASS
    parser = argparse.ArgumentParser(description="Load test / seed para security-spring")
    parser.add_argument("--api",      default=BASE_URL,   help="URL base da API (padrão: http://localhost:8080)")
    parser.add_argument("--admin",    default=ADMIN_USER, help="Usuário admin")
    parser.add_argument("--password", default=ADMIN_PASS, help="Senha do admin")
    parser.add_argument("--duration", type=int, default=60, help="Duração da rafada final em segundos (padrão: 60)")
    parser.add_argument("--setup",    action="store_true", help="Criar admin no banco (só na primeira vez)")
    args = parser.parse_args()

    BASE_URL   = args.api.rstrip("/")
    ADMIN_USER = args.admin
    ADMIN_PASS = args.password

    print("=" * 60)
    print("  security-spring — load test / seed de dados")
    print(f"  API: {BASE_URL}")
    print("=" * 60)

    # ── Setup opcional ──────────────────────────────────────────
    if args.setup:
        run_setup()
        print()

    # ── Verificar conectividade ─────────────────────────────────
    print("🔍  Verificando conectividade …")
    try:
        r = requests.get(f"{BASE_URL}/system/config/public", timeout=5)
        print(f"   API respondendo: {r.status_code}")
    except Exception as e:
        print(f"❌  Não foi possível conectar em {BASE_URL}: {e}")
        print("   Verifique se o backend está rodando.")
        sys.exit(1)

    # ── Login admin ─────────────────────────────────────────────
    print(f"\n🔐  Autenticando como {ADMIN_USER} …")
    admin_session = login_session(ADMIN_USER, ADMIN_PASS)
    if not admin_session:
        print(f"❌  Falha ao autenticar como {ADMIN_USER}.")
        print(f"   Se ainda não criou o admin, rode com --setup")
        print(f"   Ou passe a senha correta com --password SUA_SENHA")
        sys.exit(1)
    print("   ✅  Autenticado")

    # ── Criar usuários de teste ─────────────────────────────────
    usernames = create_test_users(admin_session)

    # ── Simulações ──────────────────────────────────────────────
    simulate_logins(usernames, count=40)
    simulate_failed_logins(usernames)
    simulate_admin_operations(admin_session)
    simulate_user_operations(usernames, rounds=3)
    simulate_errors()
    burst_requests(admin_session, duration_s=args.duration)

    print("\n" + "=" * 60)
    print("✅  Concluído! Abra o Grafana para ver os dados:")
    print("   http://localhost:3000/d/security-spring/security-spring")
    print("=" * 60)


if __name__ == "__main__":
    main()
