"""Funções compartilhadas pelos scripts de seed de demonstração (scripts/demo-seed/)."""
import json
import random
from datetime import datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP

try:
    import bcrypt
except ImportError:
    bcrypt = None


def esc(s: str) -> str:
    return s.replace("'", "''")


def sqs(s):
    """String SQL literal, ou NULL."""
    if s is None:
        return "NULL"
    return "'" + esc(str(s)) + "'"


def sqn(v):
    """Numérico SQL literal, ou NULL. Postgres arredonda para a escala da coluna NUMERIC no
    INSERT, mas arredondamos aqui também para não escrever ruído de ponto flutuante no SQL."""
    if v is None:
        return "NULL"
    if isinstance(v, Decimal):
        return format(v, "f")
    if isinstance(v, float):
        return format(round(v, 6), "f")
    return str(v)


def sqb(v):
    if v is None:
        return "NULL"
    return "TRUE" if v else "FALSE"


def sqts(dt):
    """Timestamp SQL literal, ou NULL."""
    if dt is None:
        return "NULL"
    return "TIMESTAMP '" + dt.strftime("%Y-%m-%d %H:%M:%S") + "'"


def sqd(d):
    if d is None:
        return "NULL"
    return "DATE '" + d.strftime("%Y-%m-%d") + "'"


def money(v) -> Decimal:
    return Decimal(str(v)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


_PASSWORD_HASH_CACHE = {}


def bcrypt_hash(password: str) -> str:
    if password not in _PASSWORD_HASH_CACHE:
        if bcrypt is None:
            raise RuntimeError(
                "Biblioteca 'bcrypt' não encontrada. Instale com: pip install bcrypt"
            )
        _PASSWORD_HASH_CACHE[password] = bcrypt.hashpw(
            password.encode("utf-8"), bcrypt.gensalt(rounds=10)
        ).decode("utf-8")
    return _PASSWORD_HASH_CACHE[password]


def gen_cpf(rng: random.Random) -> str:
    """CPF sintético com dígitos verificadores válidos (módulo 11)."""

    def dv(digits):
        s = sum(d * w for d, w in zip(digits, range(len(digits) + 1, 1, -1)))
        r = (s * 10) % 11
        return 0 if r == 10 else r

    base = [rng.randint(0, 9) for _ in range(9)]
    d1 = dv(base)
    d2 = dv(base + [d1])
    return "".join(str(d) for d in base + [d1, d2])


def gen_cnpj(rng: random.Random) -> str:
    """CNPJ sintético com dígitos verificadores válidos (matriz, filial 0001)."""

    def dv(digits, weights):
        s = sum(d * w for d, w in zip(digits, weights))
        r = s % 11
        return 0 if r < 2 else 11 - r

    base = [rng.randint(0, 9) for _ in range(8)] + [0, 0, 0, 1]
    w1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    d1 = dv(base, w1)
    w2 = [6] + w1
    d2 = dv(base + [d1], w2)
    return "".join(str(d) for d in base + [d1, d2])


def gen_phone(rng: random.Random) -> str:
    ddd = rng.choice([11, 12, 13, 21, 27, 31, 41, 47, 48, 51, 61, 71, 81, 85])
    n = rng.randint(900000000, 999999999)
    return f"({ddd}) 9{str(n)[:4]}-{str(n)[4:8]}"


def daterange_random(rng: random.Random, start: datetime, end: datetime) -> datetime:
    delta = end - start
    seconds = rng.uniform(0, max(delta.total_seconds(), 1))
    return start + timedelta(seconds=seconds)


def write_sql(path, statements, wrap_transaction=True, header=None):
    with open(path, "w", encoding="utf-8") as f:
        if header:
            f.write(header.rstrip() + "\n\n")
        if wrap_transaction:
            f.write("BEGIN;\n\n")
        for s in statements:
            f.write(s)
            if not s.endswith("\n"):
                f.write("\n")
        if wrap_transaction:
            f.write("\nCOMMIT;\n")


def write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2, default=str)


def read_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)
