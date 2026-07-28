# Coleções Postman — mahal-backend

Cada módulo tem a **sua própria coleção**, guardada junto da documentação do domínio. Toda
coleção é autossuficiente: começa com o login, guarda o `accessToken` e usa esse token nas
demais requisições, então dá para importar só a do módulo em que você está trabalhando.

| Módulo | Coleção | O que cobre | Pré-requisito manual |
|---|---|---|---|
| Estoque | [`dominios/estoque/estoque.postman_collection.json`](../dominios/estoque/estoque.postman_collection.json) | Depósitos, produtos com variações, movimentações (entrada/saída/ajuste), ponto de reposição e o alerta de estoque mínimo | — |
| Compras | [`dominios/compras/compras.postman_collection.json`](../dominios/compras/compras.postman_collection.json) | Fornecedores e recebimento de mercadoria com entrada automática no estoque | Um fornecedor cadastrado (SQL abaixo) |
| Vendas Balcão (PDV) | [`dominios/vendas-balcao/vendas-balcao.postman_collection.json`](../dominios/vendas-balcao/vendas-balcao.postman_collection.json) | **Ciclo de caixa completo** (abrir, sangria/suprimento, fechar com conferência) e venda com baixa automática de estoque, incluindo o rollback por saldo insuficiente | — (a coleção abre o caixa pela API) |
| Pedidos (admin) | [`dominios/pedido/pedido.postman_collection.json`](../dominios/pedido/pedido.postman_collection.json) | Consulta de pedidos de todos os canais com custo e margem, transição de estágio e cancelamento com estorno de estoque | Ao menos um pedido — rode a coleção do PDV antes |
| CRM | [`dominios/crm/crm.postman_collection.json`](../dominios/crm/crm.postman_collection.json) | Clientes, notas, Kanban de estágios, tags, automações de campanha, dashboard e export CSV | — |
| Plataforma | [`dominios/plataforma/plataforma.postman_collection.json`](../dominios/plataforma/plataforma.postman_collection.json) | Auth e sessões, usuários, roles, permissões, notificações, auditoria, sistema, 2FA e os casos de 401/403 | — |
| E-commerce | [`dominios/ecommerce/ecommerce.postman_collection.json`](../dominios/ecommerce/ecommerce.postman_collection.json) | Listagem de carrinhos (módulo ainda em esqueleto) | — |
| Financeiro | [`dominios/financeiro/financeiro.postman_collection.json`](../dominios/financeiro/financeiro.postman_collection.json) | Listagem do fluxo de caixa (módulo ainda em esqueleto) | — |
| Logística | [`dominios/logistica/logistica.postman_collection.json`](../dominios/logistica/logistica.postman_collection.json) | Listagem de remessas (módulo ainda em esqueleto) | — |

O environment compartilhado fica em
[`mahal-local.postman_environment.json`](mahal-local.postman_environment.json).

## Como usar no Postman

1. **Import** → arraste o `.json` da coleção do módulo (e, opcionalmente, o environment).
2. Confira as variáveis da coleção (aba *Variables*): `baseUrl` (padrão
   `http://localhost:8082`), `username` (`admin`) e `password` (`Admin@dev1`, a senha semeada
   no profile `dev`).
3. Rode a pasta **`00 — Autenticação`**. Ela salva o `accessToken` na coleção — todas as
   outras requisições herdam esse bearer automaticamente.
4. Rode as demais pastas na ordem, ou use o **Collection Runner** para executar a coleção
   inteira de uma vez.

As requisições têm testes (`pm.test`) que conferem status, corpo e regras de negócio — o
resultado aparece na aba *Test Results* de cada requisição e no relatório do Runner.

## Como rodar na linha de comando

```bash
npx newman run docs/dominios/estoque/estoque.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

Sobrescrevendo variáveis pontualmente (útil em homologação):

```bash
npx newman run docs/dominios/crm/crm.postman_collection.json \
  --env-var baseUrl=https://api.hml.exemplo \
  --env-var username=admin --env-var password='...'
```

## Convenções das coleções

- **Pasta `00 — Autenticação`** em todas: login, conclusão de 2FA (ignorada quando não há
  challenge pendente) e `GET /users/me`, que confere se o usuário logado tem as permissões
  exigidas pelo módulo. Se essa checagem falhar, o resto vai dar 403 — corrija a role antes
  de seguir.
- **Dados gerados com timestamp.** SKUs, códigos de depósito, e-mails de cliente e usernames
  recebem um sufixo de `Date.now()`, então a coleção pode ser rodada quantas vezes quiser sem
  esbarrar em conflito de unicidade.
- **Pastas de erro.** Cada módulo tem uma pasta com os caminhos infelizes (400/401/403/404/409)
  verificando o `errorCode` do `ApiError` — a tabela completa de códigos está em
  [`../api-reference.md`](../api-reference.md).
- **Requisições opcionais são ignoradas, não falham.** Quando algo depende de um dado que a
  API ainda não sabe criar (fornecedor, caixa aberto) ou de uma ação destrutiva (trocar a
  própria senha, ativar 2FA), a requisição é pulada via `pm.execution.skipRequest()` com um
  aviso no console. Na coleção da plataforma isso é controlado por variáveis `run*`.
- **Limpeza.** O que a coleção cria e o domínio sabe apagar (tags, automações, usuário de
  teste) é removido no fim. Produtos, depósitos e clientes ficam — esses domínios ainda não
  têm endpoint de exclusão.

## Pré-requisitos manuais

Dois domínios têm endpoints de escrita que dependem de um registro que **ainda não tem
endpoint de criação** (ambos estão no backlog dos respectivos módulos). Sem eles, as
requisições correspondentes são ignoradas com aviso no console — a coleção não quebra.

```sql
-- Compras: fornecedor para o recebimento de mercadoria
INSERT INTO supplier (legal_name, tax_id, email, active)
VALUES ('Fornecedor Teste Postman', '12345678000199', 'fornecedor@teste.local', TRUE);

-- PDV: sessão de caixa aberta para registrar venda
INSERT INTO cash_register_session (operator, opened_at, opening_amount, status)
VALUES ('admin', NOW(), 200.00, 'OPEN');
```

> No profile `dev` a aplicação sobe com H2 em memória e **Flyway desabilitado**: o schema vem
> do `ddl-auto` e as permissões vêm do `SeedConfig`, não das migrations. Uma consequência
> prática é que `PDV_SALE_MANAGE` — concedida apenas pela migration V57 — não existe nesse
> profile, e o registro de venda responde 403 (é o item EST-C001 do backlog de estoque). Para
> exercitar o PDV, rode contra um banco com as migrations aplicadas ou conceda a permissão na
> mão.

## Mantendo as coleções

Quando um endpoint muda ou nasce, atualize a coleção do módulo junto com o código, do mesmo
jeito que a seção *API — Endpoints* do README do domínio. Vale conferir com:

```bash
npx newman run docs/dominios/<modulo>/<modulo>.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

O contrato oficial continua sendo o Swagger (`/swagger-ui.html`) e o
[`docs/api-reference.md`](../api-reference.md); as coleções são a forma executável dele.
