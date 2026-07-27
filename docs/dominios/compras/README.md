# Domínio: compras

**Status:** 🟡 Parcial — recebimento de mercadoria operacional; fornecedores e pedidos de compra ainda em esqueleto
**Pacote Java:** `com.cernecommerce...compras`
**Rota HTTP base:** `/compras`

> ⚠️ **Auditoria de código pendente.** Este README foi atualizado em 2026-07-26 apenas para
> refletir a entrega de `EST-F009` e receber o backlog do módulo. As seções de Regras de
> Negócio, API completa, Schema e Cobertura de Testes ainda precisam ser preenchidas a partir
> do código — rode `/1-analise compras`. Padrão de referência: [`estoque`](../estoque/README.md).

## Objetivo

Reposição de estoque via fornecedores e entradas de mercadorias.

## Escopo planejado

- **Fornecedores:** cadastro (`Supplier`). 🟡 Modelo e listagem existem; falta o cadastro (`registerSupplier`).
- **Recebimento de mercadoria:** `GoodsReceipt` integrando com o domínio `estoque` para
  movimentar saldo. ✅ Implementado (EST-F009).
- **Pedidos de compra:** `PurchaseOrder`. 🟡 Pendente.

## Estrutura hexagonal

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/compras/Supplier`, `GoodsReceipt`, `GoodsReceiptItem` |
| ports/in | `core/ports/in/ComprasUseCase` |
| ports/out | `core/ports/out/compras/SupplierRepository`, `GoodsReceiptRepository` |
| service | `core/service/ComprasService` (wired em `CoreBeanConfig`, recebe `EstoqueUseCase`) |
| adapter/in | `adapter/in/controller/ComprasController` → `GET /compras/suppliers?page&size` (`COMPRAS_READ`), `POST /compras/goods-receipts` (`COMPRAS_RECEIPT_MANAGE`) |

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/compras/suppliers` | `COMPRAS_READ` | Lista fornecedores paginados (`page` ≥ 0, `size` 1–100) |
| `POST` | `/compras/goods-receipts` | `COMPRAS_RECEIPT_MANAGE` | Registra recebimento e **dá entrada no estoque** item a item. `404 SUPPLIER_NOT_FOUND` |

## Integração com estoque

`ComprasService.receiveGoods` (`core/service/ComprasService.java:44`) chama
`EstoqueUseCase.adjustStock(..., MovementType.ENTRADA, ...)` para cada item **antes** de
persistir o `GoodsReceipt`, na mesma transação — falha em qualquer item reverte o recebimento
inteiro. Detalhes em [`estoque`](../estoque/README.md#integrações-entre-domínios).

## Testes no Postman

Coleção do módulo: [`compras.postman_collection.json`](compras.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/compras/compras.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Pré-requisitos (domínio estoque)` | cria o depósito e o produto que serão recebidos |
| `02 — Fornecedores` | listagem paginada (guarda o primeiro `supplierId`) e o 400 de `size` fora do intervalo |
| `03 — Recebimento de mercadoria` | recebimento de 2 itens e a conferência de que o saldo do estoque subiu na soma exata |
| `04 — Casos de erro` | `SUPPLIER_NOT_FOUND`, `WAREHOUSE_NOT_FOUND`, itens vazios, quantidade zerada e 401 |

> **Pré-requisito:** ainda não existe endpoint para cadastrar fornecedor. Insira um pelo banco
> (SQL na descrição da coleção e em [`docs/postman/README.md`](../../postman/README.md)); sem
> fornecedor, as requisições de recebimento são ignoradas com aviso, não falham.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| COM-F001 | 🟡 Média | Feature | cadastro-fornecedor | `registerSupplier` — hoje `Supplier` é um record stub sem validação e só existe listagem. TODO em `core/ports/in/ComprasUseCase.java:13`. | Pendente |
| COM-F002 | 🟡 Média | Feature | pedido-de-compra | `PurchaseOrder` e `createPurchaseOrder`, fechando o ciclo pedido → recebimento. TODO em `core/domain/model/compras/package-info.java:7`. | Pendente |
| COM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Preencher Regras de Negócio, Schema (V58/V59/V60) e Cobertura de Testes no padrão de `estoque`. | Pendente |
| COM-C002 | 🟢 Melhoria | Correção | expor-dto-em-vez-de-record-de-dominio | `GET /compras/suppliers` retorna `PageResult<Supplier>` — o record de domínio vaza direto na API, sem DTO de resposta. | Pendente |
| COM-C003 | 🟢 Melhoria | Correção | audit-event-no-recebimento | `POST /compras/goods-receipts` não publica `AuditEvent`, diferente dos endpoints de estoque. Contraparte de `EST-C004`. | Pendente |

## Histórico de Implementações

- **2026-07-23** — `recebimento-movimenta-saldo` (EST-F009): `Supplier`, `GoodsReceipt`/`GoodsReceiptItem`, `POST /compras/goods-receipts` chamando `EstoqueUseCase.adjustStock` com `MovementType.ENTRADA` por item; RBAC `COMPRAS_RECEIPT_MANAGE`; migrations V58/V59/V60. Coberto por `ComprasServiceTest` e `ComprasControllerSecurityTest`.

## Próximos passos

- [ ] **COM-C001** — auditar o código e completar este README.
- [ ] **COM-F001** — casos de uso `registerSupplier`.
- [ ] **COM-F002** — `PurchaseOrder` + `PurchaseOrderRepository` + migration.
