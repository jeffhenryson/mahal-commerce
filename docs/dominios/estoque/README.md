# Domínio: estoque

**Status:** 🟢 Operacional — grade de produtos, saldo multi-depósito, ledger de movimentações (gravação e consulta) e alerta de ponto de reposição em produção
**Pacote Java:** `com.cernecommerce.core.domain.model.estoque`
**Rota HTTP base:** `/estoque`
**Última atualização deste doc:** 2026-07-27 (EST-F017 + EST-C001)

## Objetivo

Gerenciamento da grade de produtos e controle de inventário multi-depósito, com trilha auditável de todas as movimentações de saldo. É o domínio transacional central do sistema: tanto o PDV (venda) quanto Compras (recebimento) escrevem saldo através dele.

## Escopo planejado

- **Grade de produtos com variações:** SKU pai (`Product`) e SKUs filhos (`ProductVariant`). ✅ Implementado (EST-F001).
- **Atributos:** sabor, tamanho, cor (`ProductAttribute`). ✅ Implementado (EST-F001).
- **Multi-depósito:** loja física × e-commerce (`Warehouse` + `StockBalance` por depósito). ✅ Implementado (EST-F002).
- **Movimentações:** entradas/saídas/ajustes com histórico auditável (`StockMovement`). ✅ Implementado (EST-F003), com consulta paginada do histórico (EST-F017).
- **Ponto de reposição:** mínimo por SKU/depósito + notificação automática. ✅ Implementado (EST-F004).
- **Entrada por XML de NF-e** (`NfeXmlImportPort`). 🟡 Pendente (EST-F005).
- **Inventário, lote/validade, custo médio, transferência, reserva, kit, unidade de medida.** 🟡 Pendentes — ver [Backlog do Módulo](#backlog-do-módulo).

## Modelo de Domínio

Todos os modelos são `record` imutáveis em `core/domain/model/estoque/`, com invariantes no
compact constructor e o par de fábricas `create()` (entidade nova, sem `id`) / `of()`
(reconstituição a partir da persistência).

| Modelo | Campos | Invariantes e comportamento |
|---|---|---|
| `Product` | `id, sku, name, category, active, variants` | `sku` e `name` obrigatórios; `variants` null vira `List.of()`, senão cópia defensiva; `create` nasce `active = true` |
| `ProductVariant` | `id, sku, attributes, active` | `sku` obrigatório; cópia defensiva dos atributos |
| `ProductAttribute` | `type, value` | Ambos obrigatórios; **sem identidade própria** (persistido como `@ElementCollection`) |
| `Warehouse` | `id, code, name, type, active` | `code`, `name` e `type` obrigatórios; `create` nasce ativo |
| `WarehouseType` | enum | `LOJA_FISICA`, `ECOMMERCE` |
| `StockBalance` | `id, sku, warehouseId, quantity, version` | `quantity` **nunca negativa**; `zero(sku, warehouseId)` para saldo inicial; `version` suporta locking otimista |
| `StockMovement` | `id, sku, warehouseId, type, quantity, reason, username, createdAt` | Todos obrigatórios; `quantity` **estritamente maior que zero**; `create()` carimba `Instant.now()` |
| `MovementType` | enum | `ENTRADA`, `SAIDA`, `AJUSTE` |
| `ReorderPoint` | `id, sku, warehouseId, minQuantity` | `minQuantity >= 0`; `isBelow(qty)` é comparação **estrita** (`qty < minQuantity`) |

**Ponto central do domínio — `StockBalance.apply(MovementType, BigDecimal)`:**
`SAIDA` subtrai, `ENTRADA` e `AJUSTE` somam. Se o resultado ficaria negativo, lança
`InsufficientStockException`. Zerar exatamente é permitido; negativar não. O `version` é
preservado no record resultante, para que o merge no JPA acione o optimistic locking.

**Exceções** (`core/domain/exception/estoque/`) e o mapeamento HTTP em
`infra/handler/GlobalExceptionHandler.java`:

| Exceção | HTTP | Código de erro |
|---|---|---|
| `DuplicateSkuException` | 409 | `SKU_ALREADY_EXISTS` |
| `MissingServletRequestParameterException` (Spring) | 400 | `MISSING_PARAMETER` |
| `DuplicateWarehouseCodeException` | 409 | `WAREHOUSE_CODE_ALREADY_EXISTS` |
| `WarehouseNotFoundException` | 404 | `WAREHOUSE_NOT_FOUND` |
| `InsufficientStockException` | 400 | `INSUFFICIENT_STOCK` |
| `ObjectOptimisticLockingFailureException` (Spring) | 409 | `STOCK_UPDATE_CONFLICT` |

## Regras de Negócio Implementadas

| Regra | Onde | Teste |
|---|---|---|
| SKU do produto é único no sistema | `EstoqueService.createProduct` | `EstoqueServiceTest.createProduct_throwsWhenSkuAlreadyExists` |
| Produto pode ser criado sem variações (produto simples) | `Product.create` | `EstoqueServiceTest.createProduct_allowsProductWithoutVariants` |
| Variação exige SKU próprio; atributo exige tipo e valor | `ProductVariant`, `ProductAttribute` (compact constructors) | `@Valid` em `ProductVariantRequest` / `ProductAttributeRequest` |
| Listagem de produtos é paginada (máx. 100 por página) | `EstoqueController.listProducts` (`Math.min(size, 100)`) | `EstoqueControllerTest.list_returns_200_with_products` |
| Código de depósito (`Warehouse.code`) é único no sistema | `EstoqueService.createWarehouse` | `EstoqueServiceTest.createWarehouse_throwsWhenCodeAlreadyExists` |
| Consulta de saldo retorna zero (sem persistir linha) quando não há registro para o par SKU/depósito | `EstoqueService.getStockBalance` | `EstoqueServiceTest.getStockBalance_returnsZeroWhenNoBalanceRecordYet` |
| Operações em depósito inexistente lançam `WarehouseNotFoundException` (404) | `getStockBalance`, `adjustStock`, `setReorderPoint` | `EstoqueServiceTest.getStockBalance_throwsWhenWarehouseNotFound`, `adjustStock_throwsWhenWarehouseNotFound`, `setReorderPoint_throwsWhenWarehouseNotFound` |
| Saldo (`StockBalance.quantity`) não pode ser negativo | `StockBalance` (compact constructor) | `StockBalanceTest.throwsWhenQuantityIsNegative` |
| Saída maior que o saldo é rejeitada e **nada é persistido** (nem movimento, nem saldo) | `StockBalance.apply` → `InsufficientStockException`, antes de qualquer `save` em `EstoqueService.adjustStock` | `EstoqueServiceTest.adjustStock_saida_insufficientBalance_throwsAndDoesNotPersistAnything`, `StockBalanceTest.apply_saida_throwsInsufficientStockExceptionWhenNotEnoughBalance` |
| Saída pode drenar o saldo exatamente até zero | `StockBalance.apply` | `StockBalanceTest.apply_saida_allowsDrainingExactlyToZero` |
| Entrada sem saldo prévio parte de zero e cria a linha de saldo | `EstoqueService.adjustStock` (`orElseGet(StockBalance::zero)`) | `EstoqueServiceTest.adjustStock_entrada_withoutPriorBalance_startsFromZeroAndPersists` |
| Toda movimentação grava um `StockMovement` (ledger auditável) com motivo e usuário | `EstoqueService.adjustStock` | `EstoqueServiceTest.adjustStock_saida_decreasesExistingBalance` |
| Quantidade de movimentação deve ser estritamente maior que zero | `StockMovement` (compact constructor) + `@DecimalMin(inclusive=false)` no request | `StockMovementTest`, `EstoqueControllerTest.registerMovement_withNegativeQuantity_returns_400` |
| Escritas concorrentes no mesmo saldo resultam em 409, não em saldo corrompido | `@Version` em `StockBalanceEntity` + handler global | — (sem teste de concorrência; ver EST-C007) |
| Definir ponto de reposição é upsert: reaproveita o `id` existente do par SKU/depósito | `EstoqueService.setReorderPoint` | `EstoqueServiceTest.setReorderPoint_createsNewWhenNoneExists`, `setReorderPoint_updatesExisting` |
| Saldo abaixo do mínimo notifica todos os usuários com `ESTOQUE_STOCK_MANAGE` | `EstoqueService.notifyIfBelowReorderPoint` | `EstoqueServiceTest.adjustStock_saida_belowReorderPoint_notifiesUsersWithStockManagePermission`, `EstoqueAlertaIT` |
| Saldo igual ao mínimo **não** dispara alerta (comparação estrita) | `ReorderPoint.isBelow` | `EstoqueServiceTest.adjustStock_saida_aboveReorderPoint_doesNotNotify` |
| Sem ponto de reposição configurado, nenhuma notificação é enviada | `EstoqueService.notifyIfBelowReorderPoint` | `EstoqueServiceTest.adjustStock_withoutReorderPointConfigured_doesNotNotify` |
| Histórico de movimentações é paginado (máx. 100) e ordenado do mais recente para o mais antigo | `EstoqueController.listMovements` (`Math.min(size, 100)`) + `findBySkuAndWarehouseIdOrderByCreatedAtDesc` | `EstoqueControllerTest.listMovements_capsPageSizeAt100`, `listMovements_returns_200_with_ledger` |
| Consultar histórico de par SKU/depósito nunca movimentado devolve página vazia (200), não 404 | `EstoqueService.listMovements` | `EstoqueServiceTest.listMovements_returnsEmptyPageWhenSkuNeverMoved`, `EstoqueControllerTest.listMovements_returns_200_withEmptyPageWhenNeverMoved` |
| Histórico em depósito inexistente lança `WarehouseNotFoundException` (404) sem tocar no repositório de movimentações | `EstoqueService.listMovements` | `EstoqueServiceTest.listMovements_throwsWhenWarehouseNotFound` |
| `sku` e `warehouseCode` ausentes na query devolvem 400 `MISSING_PARAMETER` (não 500) | `GlobalExceptionHandler.handleMissingParam` | `EstoqueControllerTest.listMovements_withoutSku_returns_400`, `listMovements_withoutWarehouseCode_returns_400`, `GlobalExceptionHandlerTest.missingRequestParameter_returns400_namingTheParameter` |

## API — Endpoints

Todos exigem `bearerAuth`. Controller: `adapter/in/controller/EstoqueController.java`.

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/estoque/products` | `ESTOQUE_PRODUCT_READ` | Lista produtos paginados (`page` = 0, `size` = 20, teto de 100) |
| `POST` | `/estoque/products` | `ESTOQUE_PRODUCT_MANAGE` | Cria produto (SKU pai) com variações e atributos. `201` + `Location: /estoque/products/{sku}`; `409 SKU_ALREADY_EXISTS` |
| `POST` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_MANAGE` | Cria depósito (`LOJA_FISICA` ou `ECOMMERCE`). `201` + `Location`; `409 WAREHOUSE_CODE_ALREADY_EXISTS` |
| `GET` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_READ` | Lista todos os depósitos (**sem paginação** — ver EST-C005) |
| `GET` | `/estoque/stock-balance` | `ESTOQUE_WAREHOUSE_READ` | Consulta saldo por `sku` + `warehouseCode`. Retorna zero se nunca houve movimentação; `404 WAREHOUSE_NOT_FOUND` |
| `POST` | `/estoque/movements` | `ESTOQUE_STOCK_MANAGE` | Registra movimentação manual (`ENTRADA`/`SAIDA`/`AJUSTE`) e devolve o saldo atualizado. `201` + `Location` para o saldo; `400 INSUFFICIENT_STOCK`; `404 WAREHOUSE_NOT_FOUND`; `409 STOCK_UPDATE_CONFLICT` |
| `GET` | `/estoque/movements` | `ESTOQUE_STOCK_MANAGE` | Histórico paginado do ledger por `sku` + `warehouseCode` (`page` = 0, `size` = 20, teto de 100), mais recentes primeiro. Par nunca movimentado devolve página vazia com `200`; `404 WAREHOUSE_NOT_FOUND`; `400 MISSING_PARAMETER` |
| `PUT` | `/estoque/products/{sku}/reorder-point` | `ESTOQUE_STOCK_MANAGE` | Define a quantidade mínima do SKU no depósito (upsert). `204 No Content`; `404 WAREHOUSE_NOT_FOUND` |

**Auditoria:** o `EstoqueController` publica `AuditEvent` em quatro operações —
`PRODUCT_CREATED`, `WAREHOUSE_CREATED`, `STOCK_MOVEMENT_REGISTERED` e `REORDER_POINT_SET`.
Movimentações originadas de venda ou recebimento **não** geram `AuditEvent` (ver EST-C004).
Leituras (incluindo `GET /estoque/movements`) não geram `AuditEvent`.

**Por que o histórico exige `ESTOQUE_STOCK_MANAGE` e não `ESTOQUE_WAREHOUSE_READ`:** o ledger
carrega o `username` de quem realizou cada movimentação. Quem só precisa saber *quanto* existe
usa `GET /estoque/stock-balance` (`WAREHOUSE_READ`); ver *quem* mexeu é privilégio de quem
gerencia estoque. `EstoqueControllerSecurityTest.list_movements_with_warehouse_read_only_returns_403`
fixa essa decisão.

## Integrações entre Domínios

Estoque é consumido por outros domínios através do port de entrada `EstoqueUseCase`, injetado
em `infra/config/CoreBeanConfig.java`. As duas integrações são **chamadas síncronas diretas**
— não há evento, listener, fila nem outbox.

| Origem | Onde | Tipo | `reason` gravado no ledger |
|---|---|---|---|
| **Compras — recebimento de mercadoria** | `ComprasService.receiveGoods` (`core/service/ComprasService.java:44`) | `ENTRADA` por item | `Recebimento de mercadoria - fornecedor #{supplierId}` |
| **PDV — venda no balcão** | `PdvService.registerSale` (`core/service/PdvService.java:47`) | `SAIDA` por item | `Venda balcão sessão #{sessionId}` |

Em ambos os casos o ajuste de estoque acontece **antes** de persistir o documento de origem
(`GoodsReceipt` / `Sale`), dentro da mesma transação. Consequência: se qualquer item falhar
— tipicamente `InsufficientStockException` na venda — a operação inteira é revertida e nem o
documento nem os movimentos anteriores do mesmo lote são gravados.

O alerta de reposição (`notifyIfBelowReorderPoint`) roda por movimentação, dentro da transação
de escrita, integrando com o domínio `notification` via `NotificationUseCase.notify`.

## Schema de Banco (Migrations)

**V44 — `estoque_product`**
- `product` (id, sku UNIQUE `uk_product_sku`, name, category, active DEFAULT TRUE)
- `product_variant` (id, product_id FK → `product` ON DELETE CASCADE, sku UNIQUE `uk_product_variant_sku`, active) — índice `idx_product_variant_product_id`
- `product_attribute` (variant_id FK → `product_variant` ON DELETE CASCADE, attr_type, attr_value) — índice `idx_product_attribute_variant_id`; **sem PK própria** (`@ElementCollection`)

**V45 — `estoque_product_permissions`**
- Cria `ESTOQUE_PRODUCT_READ` e `ESTOQUE_PRODUCT_MANAGE`, concedidas a `ROLE_ADMIN` (e replicadas para `ROLE_DEV` via `DevRoleBootstrapConfig`). **Sem `ON CONFLICT DO NOTHING`** — ver EST-C006.

**V46 — `estoque_warehouse_stock_balance`**
- `warehouse` (id, code UNIQUE `uk_warehouse_code`, name, type VARCHAR(20) [`LOJA_FISICA`|`ECOMMERCE`], active)
- `stock_balance` (id, sku, warehouse_id FK → `warehouse` ON DELETE CASCADE, quantity NUMERIC(14,3) DEFAULT 0, version BIGINT DEFAULT 0) — UNIQUE `uk_stock_balance_sku_warehouse (sku, warehouse_id)`; índice `idx_stock_balance_warehouse_id`

**V47 — `estoque_warehouse_permissions`**
- Cria `ESTOQUE_WAREHOUSE_READ` e `ESTOQUE_WAREHOUSE_MANAGE` para `ROLE_ADMIN`. **Sem `ON CONFLICT DO NOTHING`** — ver EST-C006.

**V55 — `estoque_movement`**
- `stock_movement` (id, sku VARCHAR(50), warehouse_id FK → `warehouse` ON DELETE CASCADE, type VARCHAR(10), quantity NUMERIC(14,3), reason VARCHAR(255), username VARCHAR(80), created_at TIMESTAMP) — índice composto `idx_stock_movement_sku_warehouse_created (sku, warehouse_id, created_at)` para o histórico ordenado

**V56 — `estoque_movement_permissions`**
- Cria `ESTOQUE_STOCK_MANAGE` para `ROLE_ADMIN`, com `ON CONFLICT DO NOTHING`

**V61 — `stock_reorder_points`**
- `stock_reorder_point` (id, sku, warehouse_id FK → `warehouse` ON DELETE CASCADE, min_quantity NUMERIC(14,3)) — UNIQUE `uk_stock_reorder_point_sku_warehouse (sku, warehouse_id)`

**Nota de modelagem:** `stock_balance`, `stock_movement` e `stock_reorder_point` referenciam
`warehouse(id)` por FK, mas guardam `sku` como **texto livre** — não há FK para `product.sku`
nem para `product_variant.sku`. Ver EST-C002.

## Cobertura de Testes

| Arquivo | Tipo | O que cobre |
|---|---|---|
| `core/service/EstoqueServiceTest` | Unit (Mockito) | Todos os 8 casos de uso, incluindo os 3 cenários de alerta de reposição e os 3 de histórico de movimentações |
| `core/domain/model/estoque/StockBalanceTest` | Unit de domínio | `zero`/`of`, invariantes, e os 5 cenários de `apply` (entrada, saída, drenar a zero, insuficiente, ajuste) |
| `core/domain/model/estoque/StockMovementTest` | Unit de domínio | `create`/`of` e todas as invariantes |
| `core/domain/model/estoque/WarehouseTest` | Unit de domínio | `create`/`of` e obrigatoriedade de code/name/type |
| `adapter/in/controller/EstoqueControllerTest` | MockMvc standalone | 29 casos: 200/201/204/400/404/409 dos 8 endpoints |
| `adapter/in/controller/EstoqueControllerSecurityTest` | MockMvc + Security | 401 sem auth / 403 sem authority / sucesso com a authority correta, endpoint a endpoint — inclui o 403 de `WAREHOUSE_READ` no histórico de movimentações |
| `infra/config/DevRoleBootstrapConfigTest` | Unit (Mockito) | 4 casos: `ROLE_DEV` recebe as permissões de negócio (incl. `PDV_SALE_MANAGE`), as `DEV_ONLY_*`, e não cria usuário sem `DEV_EMAIL` |
| `infra/config/SeedConfigTest` | Unit (Mockito) | `ROLE_ADMIN` recebe as permissões `ESTOQUE_*` e `PDV_SALE_MANAGE` no seed de dev |
| `adapter/in/controller/EstoqueAlertaIT` | `@SpringBootTest` (profile `dev`) | E2E do alerta: depósito → ENTRADA 20 → mínimo 10 → SAIDA 12 → notificação em `GET /notifications` |
| `core/service/ComprasServiceTest` | Unit | Recebimento ajusta estoque por item; falha do estoque propaga e não salva o receipt |
| `core/service/PdvServiceTest` | Unit | Venda dá baixa por item; `InsufficientStockException` reverte a venda inteira |

**Lacunas conhecidas:** nenhum `@DataJpaTest` dos repositórios de estoque e nenhum teste de
concorrência exercitando o `@Version`. Ver EST-C007. O histórico de movimentações tem cobertura
de service, controller e segurança, mas ainda não tem um IT end-to-end que grave movimentações
reais e as releia pelo endpoint.

## Testes no Postman

Coleção do módulo: [`estoque.postman_collection.json`](estoque.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/estoque/estoque.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Depósitos` | criação, listagem e o 409 de código duplicado |
| `02 — Produtos` | criação com variações e atributos, listagem paginada, 409 de SKU duplicado e 400 de validação |
| `03 — Saldo e movimentações` | saldo zerado inicial, `ENTRADA` → `SAIDA` → `AJUSTE` conferindo o saldo a cada passo, e os erros `INSUFFICIENT_STOCK`, quantidade negativa e depósito inexistente |
| `04 — Ponto de reposição e alerta` | upsert do mínimo, saída que **não** cruza o mínimo, saída que cruza, e a conferência da notificação em `GET /notifications` |
| `05 — Segurança` | 401 sem token e com token inválido |

O SKU e o código de depósito são gerados com timestamp a cada execução, então a coleção é
reexecutável sem limpeza manual.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| EST-F005 | 🟡 Média | Feature | importacao-nfe-xml | Entrada de mercadoria por XML de NF-e (`NfeXmlImportPort`) gerando `StockMovement` de entrada — diferencial operacional. | Backlog (Sprint 4) |
| EST-F006 | 🟡 Média | Feature | inventario-contagem | Balanço/contagem cíclica com registro de divergências e ajuste automático de saldo. | Backlog (Sprint 3) |
| EST-F007 | 🟡 Média | Feature | valorizacao-custo-medio | Custo médio ponderado por SKU e valor total de estoque — alimenta o DRE do domínio `financeiro`. | Backlog (Sprint 3) |
| EST-F008 | 🟡 Média | Feature | controle-lote-validade | Lote e validade para essências/perecíveis + alerta de vencimento próximo. | Backlog (Sprint 3) |
| EST-F011 | 🟢 Baixa | Feature | curva-abc-giro | Análise ABC e giro de produtos para priorização de compras (domínio `relatorios`). | Backlog (Sprint 6) |
| EST-F012 | 🟡 Média | Feature | transferencia-entre-depositos | `MovementType.TRANSFER`: saída atômica de um `Warehouse` + entrada em outro (loja física → e-commerce), distinto do ajuste manual. | Backlog (Sprint 4) |
| EST-F013 | 🟡 Média | Feature | reserva-estoque-checkout | `StockReservation` temporária ao adicionar item ao carrinho do e-commerce, liberada se o checkout expirar — evita overselling. | Pendente |
| EST-F014 | 🟡 Média | Feature | estorno-devolucao-venda | Devolução (PDV ou e-commerce) gera `StockMovement` de entrada estornando a baixa original, com rastreabilidade da venda de origem. | Backlog (Sprint 4) |
| EST-F015 | 🟢 Baixa | Feature | kit-produto-composto | Produto "kit"/combo (ex.: kit narguilé = essência + carvão + descartável) que dá baixa nos componentes conforme receita cadastrada. | Backlog (Sprint 6) |
| EST-F016 | 🟢 Baixa | Feature | unidade-medida-conversao | Múltiplas unidades por produto (compra em kg, venda em porção/g) com fator de conversão nas movimentações. | Backlog (Sprint 6) |
| EST-F018 | 🟡 Média | Feature | atualizar-desativar-produto-deposito | Só existem `create` e `list` para produto e depósito. Falta `PUT`/`PATCH` e desativação — o campo `active` de `Product` e `Warehouse` nunca muda depois da criação. | Pendente |
| EST-C002 | 🟡 Importante | Correção | validar-existencia-do-sku | `adjustStock` e `setReorderPoint` não verificam que o SKU existe em `product`/`product_variant`, e não há FK no banco. É possível movimentar saldo e definir mínimo para um SKU inexistente ou digitado errado. | Pendente |
| EST-C003 | 🟡 Importante | Correção | notificacao-reposicao-em-loop-e-na-transacao | `notifyIfBelowReorderPoint` roda por movimentação e dentro da transação de escrita: uma venda com N itens abaixo do mínimo gera N notificações por destinatário, e o envio prolonga a transação. Agregar por operação e mover para depois do commit. | Pendente |
| EST-C004 | 🟡 Importante | Correção | audit-event-ausente-em-venda-e-recebimento | Só o `EstoqueController` publica `AuditEvent`. Movimentações vindas de `PdvService` e `ComprasService` — a maioria em volume — não deixam rastro na trilha de auditoria. | Pendente |
| EST-C005 | 🟢 Melhoria | Correção | validacao-e-paginacao-nos-endpoints-de-leitura | `GET /estoque/stock-balance` recebe `sku` e `warehouseCode` sem Bean Validation (o controller não é `@Validated`, diferente de `ComprasController` e `PdvController`); `GET /estoque/warehouses` retorna a lista inteira sem paginação. | Pendente |
| EST-C006 | 🟢 Melhoria | Correção | migrations-v45-v47-sem-on-conflict | V45 e V47 inserem permissões sem `ON CONFLICT DO NOTHING`, ao contrário de V56/V57/V60. Re-execução em base parcialmente populada quebra. Herdado do antigo C018. | Pendente |
| EST-C007 | 🟡 Importante | Correção | lacunas-de-teste-persistencia-e-concorrencia | Não há `@DataJpaTest` para os repositórios de estoque nem teste que exercite o `@Version` do `stock_balance` sob escrita concorrente — justamente o mecanismo que protege o saldo contra corrupção. | Pendente |
| EST-C008 | 🟢 Melhoria | Correção | package-info-obsoletos | `core/domain/model/estoque/package-info.java` e `core/ports/out/estoque/package-info.java` descrevem o módulo como "esqueleto (TODO)" e listam como previstos modelos e adapters que já existem há várias sprints. | Pendente |
| EST-C009 | 🟢 Melhoria | Correção | ajuste-de-inventario-so-incrementa | `StockBalance.apply` trata tudo que não é `SAIDA` como soma, então `AJUSTE` só aumenta saldo. Um ajuste de inventário para baixo hoje precisa ser lançado como `SAIDA`, o que polui a semântica do ledger. Depende da decisão de modelagem de EST-F006. | Pendente |

## Histórico de Implementações

- **2026-07-15** — `cadastrar-produto` (EST-F001): grade de produtos com SKU pai, variações e atributos; listagem paginada com padrão ID-first + `JOIN FETCH` (`ProductJpaRepository.findAllIds` + `findAllByIdsWithVariants`); RBAC `ESTOQUE_PRODUCT_READ`/`MANAGE`; migrations V44/V45.
- **2026-07-15** — `controle-saldo-multi-deposito` (EST-F002): `Warehouse` (código único, loja física/e-commerce) e `StockBalance` por SKU/depósito com `@Version`; consulta de saldo retorna zero quando ainda não houve movimentação; RBAC `ESTOQUE_WAREHOUSE_READ`/`MANAGE`; migrations V46/V47.
- **2026-07-15** — `paginacao-repositorios`: `PageResult<T>` adotado nos repositórios do módulo.
- **2026-07-22** — `movimentacao-manual` (EST-F003): `StockMovement` como ledger `ENTRADA`/`SAIDA`/`AJUSTE`, `EstoqueService.adjustStock` transacional sobre `StockBalance`, `POST /estoque/movements`, `InsufficientStockException` (400) e conflito otimista (409 `STOCK_UPDATE_CONFLICT`); RBAC `ESTOQUE_STOCK_MANAGE`; migrations V55/V56.
- **2026-07-22** — `alinhar-permissoes-seed-dev` (C006): permissões `ESTOQUE_*` acrescentadas a `SeedConfig.ADMIN_PERMISSIONS`, eliminando 403 inesperado em `/estoque/**` no perfil dev.
- **2026-07-23** — `alerta-estoque-minimo` (EST-F004): `ReorderPoint`, `PUT /estoque/products/{sku}/reorder-point` com upsert, e notificação via `NotificationUseCase` a todos os usuários com `ESTOQUE_STOCK_MANAGE` (`UserRepository.findUsernamesByPermission`); migration V61; cobertura E2E em `EstoqueAlertaIT`.
- **2026-07-23** — `recebimento-movimenta-saldo` (EST-F009, domínio `compras`): `POST /compras/goods-receipts` chama `adjustStock` com `ENTRADA` por item. Detalhes em [`docs/dominios/compras/README.md`](../compras/README.md).
- **2026-07-23** — `baixa-automatica-venda` (EST-F010, domínio `vendas-balcao`): venda no PDV chama `adjustStock` com `SAIDA` por item e dispara o alerta de reposição. Detalhes em [`docs/dominios/vendas-balcao/README.md`](../vendas-balcao/README.md).
- **2026-07-27** — `permissao-pdv-sale-manage-ausente-no-seed` (EST-C001): `PDV_SALE_MANAGE` acrescentada aos arrays `ADMIN_PERMISSIONS` de `SeedConfig` e `DevRoleBootstrapConfig`, eliminando o 403 de `ROLE_DEV` em `POST /pdv/sessions/{id}/sales` que bloqueava o caminho de baixa automática de estoque em dev. Sem migration — a V57 já cria a permissão e a concede a `ROLE_ADMIN`; o furo era só no bootstrap de runtime. Cobertura nova em `DevRoleBootstrapConfigTest` e `SeedConfigTest`.
- **2026-07-27** — `historico-movimentacoes-endpoint` (EST-F017): `GET /estoque/movements?sku=&warehouseCode=&page=&size=` liga o `StockMovementRepository.findBySkuAndWarehouseId`, que estava órfão desde EST-F003. Novo `EstoqueUseCase.listMovements` (`@Transactional(readOnly = true)`, resolve o depósito por código antes de paginar), `StockMovementResponseDTO` e `StockMovementDTOConverter.toResponse`; RBAC `ESTOQUE_STOCK_MANAGE`; sem migration (o índice `idx_stock_movement_sku_warehouse_created` da V55 já servia à consulta). Junto veio a correção de `GlobalExceptionHandler`, que não tratava `MissingServletRequestParameterException` e devolvia 500 em vez de 400 `MISSING_PARAMETER` para qualquer `@RequestParam` obrigatório ausente — afetava também o `GET /estoque/stock-balance` já existente.

## Próximos passos

Prioridade sugerida, na ordem:

1. **EST-C002** — validar SKU antes que a base acumule saldo órfão.
2. **EST-C003** — notificação de reposição em loop e dentro da transação de escrita.
3. **EST-C004** — `AuditEvent` ausente nas movimentações de venda e recebimento, que são a maioria em volume. Agora que o ledger é legível pelo endpoint, a lacuna de trilha fica mais visível.
4. **EST-F006** (inventário/contagem) — resolvendo junto a semântica de `AJUSTE` (EST-C009).
5. **EST-C007** — `@DataJpaTest` dos repositórios e teste de concorrência do `@Version`.
