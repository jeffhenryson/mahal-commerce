# Domínio: compras

**Status:** 🟡 Parcial — recebimento de mercadoria operacional; fornecedores e pedidos de compra ainda em esqueleto
**Pacote Java:** `com.cernecommerce...compras`
**Rota HTTP base:** `/compras`
**Última atualização deste doc:** 2026-08-18 (EST-F005 — importação de NF-e via XML, endpoints novos neste módulo; COM-F003, COM-C004 — auditoria `/1-analise ambas` + `Docs/BACKEND_TODO.md` do `mahal-admin`)

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
- **Importação de NF-e:** entrada de mercadoria automática lendo o XML da nota fiscal do
  fornecedor, em duas fases (preview com casamento por CNPJ/EAN → confirm com override manual e
  `GoodsReceipt` real). ✅ Implementado (EST-F005, 2026-08-18).
- **Pedidos de compra:** `PurchaseOrder`. 🟡 Pendente.

## Estrutura hexagonal

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/compras/Supplier`, `GoodsReceipt`, `GoodsReceiptItem`, `NfeImport`, `NfeImportLine` (EST-F005) |
| ports/in | `core/ports/in/ComprasUseCase`, `core/ports/in/NfeImportUseCase` (EST-F005) |
| ports/out | `core/ports/out/compras/SupplierRepository`, `GoodsReceiptRepository`, `NfeImportRepository`; `core/ports/out/estoque/NfeXmlImportPort` (implementado em `adapter/out/nfe/JdkDomNfeXmlImportAdapter`); `core/ports/out/storage/NfeImportStoragePort` |
| service | `core/service/ComprasService` (wired em `CoreBeanConfig`, recebe `EstoqueUseCase`) |
| adapter/in | `adapter/in/controller/ComprasController` → `GET /compras/suppliers?page&size` (`COMPRAS_READ`), `POST /compras/goods-receipts` (`COMPRAS_RECEIPT_MANAGE`); `adapter/in/controller/NfeImportController` (EST-F005) → `POST /compras/goods-receipts/nfe-preview`/`.../nfe-confirm` (`COMPRAS_RECEIPT_MANAGE`) |

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/compras/suppliers` | `COMPRAS_READ` | Lista fornecedores paginados (`page` ≥ 0, `size` 1–100) |
| `POST` | `/compras/goods-receipts` | `COMPRAS_RECEIPT_MANAGE` | Registra recebimento e **dá entrada no estoque** item a item. `404 SUPPLIER_NOT_FOUND` |
| `POST` | `/compras/goods-receipts/nfe-preview` | `COMPRAS_RECEIPT_MANAGE` | EST-F005 — parseia XML de NF-e (multipart), casa fornecedor por CNPJ e itens por EAN, sem persistir recebimento. `400 MALFORMED_NFE_XML`; `404 SUPPLIER_NOT_FOUND_BY_TAX_ID` |
| `POST` | `/compras/goods-receipts/nfe-confirm` | `COMPRAS_RECEIPT_MANAGE` | EST-F005 — confirma um preview (com override manual de SKU para linhas `UNMATCHED`) e delega para o mesmo caminho de `POST /compras/goods-receipts`. `400 UNMATCHED_NFE_LINE`; `404 NFE_IMPORT_NOT_FOUND`; `409 NFE_IMPORT_ALREADY_PROCESSED` |

## Segurança e Infraestrutura

> Mecanismos transversais em [`docs/security.md`](../../security.md); ambientes e containers em
> [`docs/infrastructure.md`](../../infrastructure.md); o modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura). Aqui fica só o recorte
> deste domínio.

### Permissões RBAC

| Permissão | Libera | Migration | Semeada em `dev`? |
|---|---|---|---|
| `COMPRAS_READ` | `GET /compras/suppliers` | V53 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `COMPRAS_RECEIPT_MANAGE` | `POST /compras/goods-receipts` | V60 (com `ON CONFLICT DO NOTHING`) | ✅ |

⚠️ **`COMPRAS_RECEIPT_MANAGE` movimenta estoque sem exigir nenhuma permissão `ESTOQUE_*`.**
`ComprasService.receiveGoods` chama `EstoqueUseCase.adjustStock` diretamente, e o
`@PreAuthorize` só existe na borda HTTP. Quem pode receber mercadoria pode aumentar o saldo de
qualquer SKU em qualquer depósito, com o motivo `Recebimento de mercadoria - fornecedor #{id}`.
É o desenho pretendido (o port é a fronteira), mas precisa ser considerado ao conceder a
permissão.

### Rate limiting

❌ Nenhum endpoint deste módulo é limitado — o `LoginRateLimitingFilter` cobre apenas
`/auth/**` e duas rotas de notificação. Ver PLAT-C030.

### Isolamento de dados

Single-tenant: quem tem `COMPRAS_READ` vê todos os fornecedores; não há vínculo
usuário↔fornecedor nem usuário↔depósito.

### Auditoria

✅ `POST /compras/goods-receipts` publica `AuditEvent` do tipo `STOCK_MOVEMENT_REGISTERED`, com
`origin: GOODS_RECEIPT`, `supplierId`, `warehouseCode`, `type` e a lista de `skus` recebidos — um
evento por recebimento, não por item; o detalhamento item a item segue no ledger `stock_movement`,
que também guarda `username` e `reason`. Resolvido em COM-C003 / `EST-C004` (2026-07-27).

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `supplier`, `goods_receipt`, `goods_receipt_item` (V58/V59) | módulo indisponível |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior |
| `EstoqueUseCase` (chamada síncrona in-process) | entrada de saldo por item | recebimento inteiro falha e reverte |

Sem fila, sem outbox e sem storage de arquivos. O recebimento e os movimentos de estoque
compartilham a **mesma transação**: falha em qualquer item reverte tudo.

### Limites operacionais

- `GET /compras/suppliers`: `page` ≥ 0 e `size` entre 1 e 100, validados via Bean Validation
  (o controller é `@Validated`). Até 2026-07-27 esse limite devolvia **500**, não 400: a
  `HandlerMethodValidationException` lançada pela validação nativa do Spring não tinha handler e
  caía no catch-all do `GlobalExceptionHandler`. Corrigido junto com EST-C005 — ver
  [`estoque`](../estoque/README.md#histórico-de-implementações).
- `POST /compras/goods-receipts`: itens obrigatórios e quantidade > 0 via `@Valid` nos requests;
  **não há teto para o número de itens** de um recebimento — cada item vira uma escrita de saldo
  na mesma transação.

### Riscos conhecidos

- **COM-C002** — `GET /compras/suppliers` devolve o record de domínio direto, sem DTO.
- **PLAT-C030** — sem rate limit.

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
| COM-F001 | 🔴 Alta | Feature | cadastro-fornecedor | `registerSupplier` — hoje `Supplier` é um record stub sem validação e só existe listagem (`GET /compras/suppliers`). TODO em `core/ports/in/ComprasUseCase.java:13`. Faltam `POST` (criar) e `PATCH` (editar/ativar-desativar) — confirmado como bloqueio real pelo front (`mahal-admin`, `Docs/BACKEND_TODO.md`, seção "P2 — Compras", 2026-08-18): sem isso não há como testar o resto da tela de Compras. **Segundo bloqueio real, achado em EST-F005 (2026-08-18):** a importação de NF-e rejeita com 404 qualquer nota de fornecedor não cadastrado (decisão deliberada — sem criação automática, diferente de Categoria), então hoje o único jeito de cadastrar o fornecedor antes de importar é inserção direta no banco. | Pendente |
| COM-F002 | 🟡 Média | Feature | pedido-de-compra | `PurchaseOrder` e `createPurchaseOrder`, fechando o ciclo pedido → recebimento. TODO em `core/domain/model/compras/package-info.java:7`. Workflow de status esperado pelo front: rascunho → enviado → parcialmente recebido → recebido → cancelado, linkado ao `POST /compras/goods-receipts` já existente (hoje o recebimento é "solto", sem referenciar um pedido formal). Também é a extensão natural do alerta de ponto de reposição do estoque (EST-F004): hoje o alerta não gera nenhuma ação, o gestor decide comprar de cabeça — o pedido de compra pode sugerir itens a partir do relatório de reposição já existente. | Pendente |
| COM-F003 | 🟢 Baixa | Feature | cotacoes-rfq | Solicitar cotação a um ou mais fornecedores, registrar respostas (preço, prazo, condições de pagamento), comparar e converter a vencedora em Pedido de Compra (`COM-F002`). Depende de `COM-F001`/`COM-F002` existirem primeiro. Ver `Docs/MODULO_COMPRAS.md` no `mahal-admin` para a especificação de tela original. Pedido confirmado pelo front em `Docs/BACKEND_TODO.md`, seção "P2 — Compras", 2026-08-18. | Pendente |
| COM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Preencher Regras de Negócio, Schema (V58/V59/V60) e Cobertura de Testes no padrão de `estoque`. | Pendente |
| COM-C002 | 🟢 Melhoria | Correção | expor-dto-em-vez-de-record-de-dominio | `GET /compras/suppliers` retorna `PageResult<Supplier>` — o record de domínio vaza direto na API, sem DTO de resposta. | Pendente |
| COM-C004 | 🟡 Importante | Correção | recebimento-sem-teste-de-concorrencia | Nenhum IT de concorrência cobre `SupplierRepositoryImpl`/`GoodsReceiptRepositoryImpl` (dois recebimentos concorrentes incrementando o mesmo lote/saldo) — comparado ao rigor já aplicado em `StockBalanceConcurrencyIT`/`StockCountConcurrencyIT` em `estoque`, essa lacuna destoa do padrão do projeto. Achado em auditoria `analyze-domain`/testes de 2026-08-18. | Pendente |

## Histórico de Implementações

- **2026-08-18** — `importacao-nfe-xml` (EST-F005): entrada de mercadoria automática lendo o XML
  de NF-e do fornecedor. Detalhamento completo (design, hardening contra XXE, casamento por
  EAN/CNPJ, migration V106) está em `docs/dominios/estoque/README.md` — o ID é `EST-*` porque a
  feature nasceu do backlog de estoque, mas os dois endpoints novos
  (`POST /compras/goods-receipts/nfe-preview`/`.../nfe-confirm`) vivem neste módulo, sob a mesma
  permissão `COMPRAS_RECEIPT_MANAGE` de `POST /compras/goods-receipts`. Achado de passagem: expôs
  **COM-F001** como bloqueio de verdade também para este fluxo (fornecedor não cadastrado não tem
  como ser criado antes de importar a primeira NF-e dele).
- **2026-07-27** — `size-fora-da-faixa-retornava-500` (efeito colateral do EST-C005): os `@Min(1) @Max(100)` de `GET /compras/suppliers` existiam desde COM-F001, mas nunca tinham sido exercitados por teste — e devolviam 500. Desde o Spring Framework 6.1 a validação de parâmetro de handler é aplicada pelo `RequestMappingHandlerAdapter` e lança `HandlerMethodValidationException`, não `ConstraintViolationException`; sem handler para ela, o catch-all de `Exception` do `GlobalExceptionHandler` transformava a violação em 500 `INTERNAL_ERROR`. Novo handler → 400 `VALIDATION_ERROR`, que é o que a coleção Postman deste módulo já documentava. Descoberto ao anotar o `EstoqueController` no EST-C005.
- **2026-07-23** — `recebimento-movimenta-saldo` (EST-F009): `Supplier`, `GoodsReceipt`/`GoodsReceiptItem`, `POST /compras/goods-receipts` chamando `EstoqueUseCase.adjustStock` com `MovementType.ENTRADA` por item; RBAC `COMPRAS_RECEIPT_MANAGE`; migrations V58/V59/V60. Coberto por `ComprasServiceTest` e `ComprasControllerSecurityTest`.

## Próximos passos

- [ ] **COM-C001** — auditar o código e completar este README.
- [ ] **COM-F001** — casos de uso `registerSupplier`.
- [ ] **COM-F002** — `PurchaseOrder` + `PurchaseOrderRepository` + migration.
