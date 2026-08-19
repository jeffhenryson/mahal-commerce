# Seed de demonstração (scripts/demo-seed)

Popula um banco Postgres de **hml** com ~12 meses de histórico de uso simulado do Mahal, para
demonstrações comerciais do SaaS: catálogo de produtos e kits, clientes CRM em vários estágios,
fornecedores e recebimento de mercadoria, sessões de caixa/comandas/pedidos de marketplace, e
fluxo de caixa financeiro. Ver o plano completo em
`/home/jeff-henryson/.claude/plans/quero-montar-um-plano-generic-lobster.md`.

## Como rodar

Precisa de `python3` com a biblioteca `bcrypt` instalada (`pip install bcrypt`) e de `psql` no
PATH, apontando para o Postgres de **hml** (`docker-compose.hml.yml`, porta 5435 por padrão).

```bash
cd scripts/demo-seed
MAHAL_SEED_TARGET=hml DB_HOST=localhost DB_PORT=5435 DB_NAME=mahal \
  DB_USERNAME=postgres DB_PASSWORD=*** ./run.sh
```

`run.sh` gera o SQL de cada fase (`01_usuarios.py` ... `09_financeiro.py`, todos escrevendo em
`out/`), aplica `00_reset.sql`, aplica cada fase em ordem via `psql`, e roda `10_validacao.sql`
no final. Pede confirmação interativa antes do `TRUNCATE` — passe `--yes` para pular (uso
não-interativo/CI).

**MAHAL_SEED_TARGET=hml é obrigatório** — o script se recusa a rodar sem essa variável, para
nunca apontar essa carga destrutiva para prod por engano. As credenciais do banco são sempre
parâmetros explícitos (nunca lidas de `.env` automaticamente).

## Login das contas de demonstração

**Login de vitrine (o que mostrar na demo)**: usuário **`administrador`**, senha
**`Administrador@2026!`** — conta fixa, `ROLE_ADMIN`, criada/atualizada a cada rodada (fase 01
faz `ON CONFLICT DO UPDATE` nela especificamente, então a senha nunca fica desatualizada). Como
o username não tem o prefixo `demo.`, ela sobrevive ao `DELETE` seletivo de `00_reset.sql` e não
precisa ser recriada manualmente entre execuções.

Os demais operadores fictícios (25, papéis variados) usam a senha compartilhada
**`Demo@2026`** (constante `DEMO_PASSWORD` em `config.py`), com username prefixado `demo.` (ex.
`demo.joao.silva`) e e-mail no domínio `@mahaldemo.local`. Se algum outro usuário "real" já
existir em hml (ex. `desenvolvedor`/`atendente`/`usuario` de `scripts/rename-and-seed-users.sql`),
não é tocado — só `user_type='CUSTOMER'` e `username LIKE 'demo.%'` são removidos no reset.

## Reset / reexecução

`00_reset.sql` faz `TRUNCATE ... RESTART IDENTITY CASCADE` nas tabelas de negócio (catálogo,
estoque, compras, PDV, pedidos, CRM, financeiro) e um `DELETE` seletivo em `users`/`customers`
(preserva usuários OPERADOR "reais", remove todo `user_type='CUSTOMER'` e todo `username LIKE
'demo.%'`). `roles`/`permissions`/`role_permissions` nunca são tocadas. É seguro rodar o
pipeline inteiro de novo do zero quantas vezes forem necessárias — não há merge incremental,
é sempre "apaga tudo e recria".

## Ordem das fases (grafo de dependência)

```
00_reset → 01_usuarios → 02_warehouses → 03_catalogo → 04_clientes_crm →
05_fornecedores_compras → 06_estoque_inicial → 07_vendas → 08_estoque_reposicao →
09_financeiro → 10_validacao
```

Cada fase Python lê a(s) fase(s) anterior(es) via JSON em `out/` (não reconsulta o banco), e
referências entre linhas de um `INSERT` para outro dentro do mesmo arquivo `.sql` usam
variáveis do `psql` (`RETURNING id AS x \gset` + `:x`) — por isso as fases são aplicadas via
`psql -f`, não por um executor de SQL genérico.

## Simplificações conhecidas (escopo cortado deliberadamente)

- **Sem resgate de cashback**: só lançamentos `EARNED` (e `REVERSED` em reembolso) são gerados.
  `sales_order.cashback_redeemed` fica sempre 0. Resgate pode ser demonstrado ao vivo pela API.
- **Preço/custo estáticos ao longo do tempo**: `order_item.unit_price`/`cost_price` usam o preço
  atual do catálogo (ou o custo médio acumulado até a data da venda, quando disponível) — não há
  reprecificação simulada do catálogo em si.
- **NF-e sintética**: os XMLs em `data/nfe-imports/` são fixtures de teste minúsculas, não NF-e
  reais — os registros de auditoria (`nfe_import`/`nfe_import_line`) são gerados diretamente em
  SQL, não via upload real do parser.
- **Financeiro agregado**: `cash_flow_entry` de receita (`VENDA_PDV`/`VENDA_ECOMMERCE`) é
  mensal/por canal, não um lançamento por `sales_order` — não há como referenciar o `id` de um
  pedido específico em tempo de geração (só existe depois que o INSERT roda).
- **Um único depósito físico + um único CD**: `LOJA-01` (PDV/comandas) e `CD-ECOM-01`
  (marketplace) — o schema suporta mais depósitos, mas dois já bastam para a demonstração.

## Verificação pós-carga

Além de `10_validacao.sql`, rode as coleções Postman existentes contra hml para validar pela
ótica da API (não só do schema):

```bash
npx newman run ../../docs/dominios/crm/crm.postman_collection.json \
  -e ../../docs/postman/mahal-local.postman_environment.json --env-var baseUrl=<url-do-hml>
```

Confira em especial: relatório de margem (`/orders` analytics) com números não-nulos, alerta de
estoque mínimo nos SKUs da fase 08, um pedido de marketplace com kit no carrinho, e o extrato de
cashback de um cliente com CPF cadastrado.
