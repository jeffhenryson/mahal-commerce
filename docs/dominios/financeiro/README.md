# Domínio: financeiro

**Status:** 🟡 Esqueleto criado — implementação pendente
**Pacote Java:** `com.cernecommerce...financeiro`
**Rota HTTP base:** `/financeiro`

## Objetivo

Gestão financeira integrada do lounge físico e do e-commerce.

## Escopo planejado

- **DRE simplificado:** apuração de resultado por período (`DreLine`).
- **Fluxo de caixa:** lançamentos de entrada/saída (`CashFlowEntry`).
- **Conciliação de taxas de gateways:** repasse × venda, taxas por gateway
  (`GatewayFee` + `Reconciliation`).

## Estrutura hexagonal (criada)

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/financeiro/CashFlowEntry` |
| ports/in | `core/ports/in/FinanceiroUseCase` |
| ports/out | `core/ports/out/financeiro/LedgerRepository` |
| service | `core/service/FinanceiroService` (stub, wired em `CoreBeanConfig`) |
| adapter/in | `adapter/in/controller/FinanceiroController` → `GET /financeiro/cash-flow?page&size` (stub, retorna `PageResult<CashFlowEntry>` vazio) |

## Testes no Postman

Coleção do módulo: [`financeiro.postman_collection.json`](financeiro.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/financeiro/financeiro.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

A coleção valida o que já existe (`GET /financeiro/cash-flow`): o contrato de `PageResult`, a
validação de `page`/`size` e a proteção por `FINANCEIRO_READ` — e serve de esqueleto para
crescer junto com o módulo.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| FIN-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README ainda no molde de esqueleto: faltam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. Rode `/1-analise financeiro`. Padrão: [`estoque`](../estoque/README.md). | Pendente |

> `EST-F007` (`valorizacao-custo-medio`, que alimenta o DRE) é um cruzamento
> `estoque↔financeiro` e está rastreado em [`estoque`](../estoque/README.md#backlog-do-módulo).

## Próximos passos

- [ ] Modelos: `DreLine`, `GatewayFee`, `Reconciliation`.
- [ ] Casos de uso: `buildDre`, `reconcileGatewayFees`, consulta de fluxo de caixa por período.
- [ ] Ports out: `GatewayFeeReconciliationPort` (importação de repasses/taxas dos gateways).
- [ ] Adapter de persistência: entities JPA + repository impl + migration Flyway.
- [ ] Permissões RBAC do domínio + `@PreAuthorize` nos endpoints.
