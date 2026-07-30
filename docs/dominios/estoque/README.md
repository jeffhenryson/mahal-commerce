# Domínio: estoque

**Status:** 🟢 Operacional — grade de produtos, saldo multi-depósito, ledger de movimentações (gravação e consulta) e alerta de ponto de reposição em produção
**Pacote Java:** `com.cernecommerce.core.domain.model.estoque`
**Rota HTTP base:** `/estoque`
**Última atualização deste doc:** 2026-07-27 (sprint de integridade: EST-C002, C003, C004, C007, C008, C010, C011, C012)

## Objetivo

Gerenciamento da grade de produtos e controle de inventário multi-depósito, com trilha auditável de todas as movimentações de saldo. É o domínio transacional central do sistema: tanto o PDV (venda) quanto Compras (recebimento) escrevem saldo através dele.

## Escopo planejado

- **Grade de produtos com variações:** SKU pai (`Product`) e SKUs filhos (`ProductVariant`). ✅ Implementado (EST-F001).
- **Atributos:** sabor, tamanho, cor (`ProductAttribute`). ✅ Implementado (EST-F001).
- **Multi-depósito:** loja física × e-commerce (`Warehouse` + `StockBalance` por depósito). ✅ Implementado (EST-F002).
- **Movimentações:** entradas/saídas/ajustes com histórico auditável (`StockMovement`). ✅ Implementado (EST-F003), com consulta paginada do histórico (EST-F017).
- **Ponto de reposição:** mínimo por SKU/depósito + notificação automática. ✅ Implementado (EST-F004).
- **Entrada por XML de NF-e** (`NfeXmlImportPort`). 🟡 Pendente (EST-F005).
- **Inventário/balanço:** contagem com sessão por depósito, divergência registrada e ajuste em lote no fechamento (`StockCount`). ✅ Implementado (EST-F006).
- **Precificação:** custo de aquisição, markup desejado e preço praticado por produto, com preço sugerido, margem e markup efetivo derivados. ✅ Implementado (EST-F019).
- **Lote/validade, custo médio, transferência, reserva, kit, unidade de medida.** 🟡 Pendentes — ver [Backlog do Módulo](#backlog-do-módulo).

## Modelo de Domínio

Todos os modelos são `record` imutáveis em `core/domain/model/estoque/`, com invariantes no
compact constructor e o par de fábricas `create()` (entidade nova, sem `id`) / `of()`
(reconstituição a partir da persistência).

| Modelo | Campos | Invariantes e comportamento |
|---|---|---|
| `Product` | `id, sku, name, category, active, variants, pricing` | `sku` e `name` obrigatórios; `variants` null vira `List.of()`, senão cópia defensiva; `pricing` null vira `Pricing.empty()`; `create` nasce `active = true` |
| `Pricing` | `costPrice, markupPercent, salePrice` | Value object (EST-F019); os três campos são opcionais e **não negativos**; `empty()` é "não precificado". Deriva `suggestedPrice`, `effectivePrice`, `marginAmount`, `marginPercent`, `effectiveMarkupPercent` |
| `ProductVariant` | `id, sku, attributes, active` | `sku` obrigatório; cópia defensiva dos atributos |
| `ProductAttribute` | `type, value` | Ambos obrigatórios; **sem identidade própria** (persistido como `@ElementCollection`) |
| `Warehouse` | `id, code, name, type, active` | `code`, `name` e `type` obrigatórios; `create` nasce ativo |
| `WarehouseType` | enum | `LOJA_FISICA`, `ECOMMERCE` |
| `StockBalance` | `id, sku, warehouseId, quantity, version` | `quantity` **nunca negativa**; `zero(sku, warehouseId)` para saldo inicial; `version` suporta locking otimista |
| `StockMovement` | `id, sku, warehouseId, type, quantity, reason, username, createdAt` | Todos obrigatórios; `quantity > 0` em `ENTRADA`/`SAIDA` e `>= 0` em `AJUSTE`; `create()` carimba `Instant.now()` |
| `MovementType` | enum | `ENTRADA`, `SAIDA` (delta) e `AJUSTE` (**saldo-alvo**, EST-C009) |
| `StockCount` | `id, warehouseId, status, username, createdAt, closedAt, items` | Balanço de um depósito; `withCountedItem` é upsert por SKU preservando a posição; `closed()`/`cancelled()` carimbam `closedAt` |
| `StockCountStatus` | enum | `ABERTA`, `FECHADA`, `CANCELADA` |
| `StockCountItem` | `id, sku, countedQuantity, expectedQuantity, difference` | `countedQuantity >= 0`; `expectedQuantity`/`difference` só no fechamento; `diverges()` decide se gera movimentação |
| `ReorderPoint` | `id, sku, warehouseId, minQuantity` | `minQuantity >= 0`; `isBelow(qty)` é comparação **estrita** (`qty < minQuantity`) |

**Ponto central do domínio — `StockBalance.apply(MovementType, BigDecimal)`:**
`ENTRADA` soma e `SAIDA` subtrai — ambas tratam a quantidade como **delta**. Se o resultado
ficaria negativo, lança `InsufficientStockException`; zerar exatamente é permitido, negativar não.

`AJUSTE` é a exceção: a quantidade é o **saldo-alvo**, não um delta (EST-C009). O saldo passa a
valer exatamente o valor informado, para cima ou para baixo, e zero é um alvo válido. Alvo
negativo é `IllegalArgumentException`, não `InsufficientStockException` — não existe "saldo
insuficiente" para uma contagem, o que há é um alvo inválido.

O `version` é preservado no record resultante nos três casos, para que o merge no JPA acione o
optimistic locking.

**Exceções** (`core/domain/exception/estoque/`) e o mapeamento HTTP em
`infra/handler/GlobalExceptionHandler.java`:

| Exceção | HTTP | Código de erro |
|---|---|---|
| `DuplicateSkuException` | 409 | `SKU_ALREADY_EXISTS` |
| `MissingServletRequestParameterException` (Spring) | 400 | `MISSING_PARAMETER` |
| `HandlerMethodValidationException` (Spring) | 400 | `VALIDATION_ERROR` |
| `DuplicateWarehouseCodeException` | 409 | `WAREHOUSE_CODE_ALREADY_EXISTS` |
| `WarehouseNotFoundException` | 404 | `WAREHOUSE_NOT_FOUND` |
| `ProductNotFoundException` | 404 | `PRODUCT_NOT_FOUND` |
| `InsufficientStockException` | 400 | `INSUFFICIENT_STOCK` |
| `InactiveProductException` | 409 | `PRODUCT_INACTIVE` |
| `InactiveWarehouseException` | 409 | `WAREHOUSE_INACTIVE` |
| `StockCountNotFoundException` | 404 | `STOCK_COUNT_NOT_FOUND` |
| `StockCountNotOpenException` | 409 | `STOCK_COUNT_NOT_OPEN` |
| `StockCountAlreadyOpenException` | 409 | `STOCK_COUNT_ALREADY_OPEN` |
| `ObjectOptimisticLockingFailureException` (Spring) | 409 | `STOCK_UPDATE_CONFLICT` |
| `DataIntegrityViolationException` (Spring) | 409 | `DATA_INTEGRITY_VIOLATION` |

## Regras de Negócio Implementadas

| Regra | Onde | Teste |
|---|---|---|
| SKU é único no sistema, e o espaço de nomes é compartilhado entre SKU pai e SKUs de variação | `EstoqueService.createProduct` | `EstoqueServiceTest.createProduct_throwsWhenSkuAlreadyExists`, `createProduct_throwsWhenVariantSkuEqualsParentSku` |
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
| Escritas concorrentes no mesmo saldo resultam em 409, não em saldo corrompido | `@Version` em `StockBalanceEntity` + handler global | `StockBalanceConcurrencyIT.saidas_concorrentes_nao_perdem_baixa_de_estoque` |
| Primeira movimentação concorrente do mesmo par também resulta em 409, não em 500 | `uk_stock_balance_sku_warehouse` + `GlobalExceptionHandler.handleDataIntegrityViolation` | `StockBalanceConcurrencyIT.primeira_movimentacao_concorrente_do_mesmo_par_nao_duplica_saldo` |
| Movimentar ou definir mínimo exige SKU existente no catálogo (pai ou variação) | `EstoqueService.requireKnownSku` → `ProductNotFoundException` | `EstoqueServiceTest.adjustStock_throwsWhenSkuNotInCatalog`, `adjustStock_acceptsVariantSkuNotJustParentSku`, `setReorderPoint_throwsWhenSkuNotInCatalog`, `EstoqueRepositoryIT.existsBySku_encontraTantoSkuPaiQuantoSkuDeVariacao` |
| SKU desconhecido vindo de venda ou recebimento reverte a operação inteira | propagação de `ProductNotFoundException` por `PdvService`/`ComprasService` | `PdvServiceTest.registerSale_propagatesUnknownSkuAndDoesNotSaveSale`, `ComprasServiceTest.receiveGoods_propagatesUnknownSkuAndDoesNotSaveReceipt` |
| SKU de variação duplicado é 409, não 500 | `EstoqueService.createProduct` valida pai e variações | `EstoqueServiceTest.createProduct_throwsWhenVariantSkuAlreadyExists`, `createProduct_throwsWhenPayloadRepeatsTheSameVariantSku`, `createProduct_throwsWhenVariantSkuEqualsParentSku` |
| Definir ponto de reposição é upsert: reaproveita o `id` existente do par SKU/depósito | `EstoqueService.setReorderPoint` | `EstoqueServiceTest.setReorderPoint_createsNewWhenNoneExists`, `setReorderPoint_updatesExisting` |
| Saldo abaixo do mínimo notifica todos os usuários com `ESTOQUE_STOCK_MANAGE` | `EstoqueService.notifyIfBelowReorderPoint` | `EstoqueServiceTest.adjustStock_saida_belowReorderPoint_notifiesUsersWithStockManagePermission`, `EstoqueAlertaIT` |
| Alerta é agregado por operação e só sai depois do commit | `AfterCommitExecutor` + `EstoqueService.dispatchReorderAlerts` | `TransactionAfterCommitExecutorTest.comTransacaoAtiva_agrega_e_despacha_uma_unica_vez_no_commit`, `em_rollback_nao_despacha_nada` |
| Saldo igual ao mínimo **não** dispara alerta (comparação estrita) | `ReorderPoint.isBelow` | `EstoqueServiceTest.adjustStock_saida_aboveReorderPoint_doesNotNotify` |
| Sem ponto de reposição configurado, nenhuma notificação é enviada | `EstoqueService.notifyIfBelowReorderPoint` | `EstoqueServiceTest.adjustStock_withoutReorderPointConfigured_doesNotNotify` |
| Histórico de movimentações é paginado e ordenado do mais recente para o mais antigo | `EstoqueController.listMovements` + `findBySkuAndWarehouseIdOrderByCreatedAtDescIdDesc` | `EstoqueControllerTest.listMovements_returns_200_with_ledger` |
| `page >= 0` e `size` entre 1 e 100 nos quatro endpoints paginados; fora da faixa é 400, não teto silencioso | `@Min`/`@Max` nos `@RequestParam` + `@Validated` no controller | `EstoqueControllerValidationTest.sizeAcimaDoTeto_returns_400`, `sizeZero_returns_400`, `pageNegativa_returns_400`, `sizeExatamenteNoTeto_returns_200` |
| `sku` (3–50) e `warehouseCode` (2–50) em query e path são validados antes de chegar ao service | `@NotBlank`/`@Size` nos `@RequestParam`/`@PathVariable` | `EstoqueControllerValidationTest.getStockBalance_comParametroInvalido_returns_400`, `getStockBalance_comSkuAcimaDe50Caracteres_returns_400` |
| Parâmetro **ausente** continua sendo 400 `MISSING_PARAMETER`, e não `VALIDATION_ERROR` | `GlobalExceptionHandler.handleMissingParam` vs. `handleHandlerMethodValidation` | `EstoqueControllerValidationTest.listMovements_semSku_continua_400_MISSING_PARAMETER` |
| Listagem de depósitos é paginada e ordenada por id (paginação estável) | `WarehouseJpaRepository.findAllOrderById(Pageable)` | `EstoqueServiceTest.listWarehouses_delegatesPagingToRepository`, `EstoqueRepositoryIT.warehouse_paginaOrdenadoPorId` |
| Edição de produto/depósito é parcial: campo ausente é mantido | `Product.withDetails`, `Warehouse.withDetails` (null = manter) | `ProductTest.withDetails_nullMantemOCampo`, `WarehouseTest.withDetails_nullMantemOCampo` |
| Edição não altera `sku`, `code` nem as variações | `withDetails` preserva esses campos | `ProductTest.withDetails_preservaIdSkuActiveEVariacoes`, `EstoqueRepositoryIT.save_aposWithDetails_preservaVariacoesEAtributos` |
| Edição parcial não burla os invariantes do modelo (nome em branco continua rejeitado) | compact constructor roda em cada `with*` | `ProductTest.withDetails_naoDeixaBurlarOsInvariantes`, `WarehouseTest.withDetails_naoDeixaBurlarOsInvariantes` |
| Produto ou depósito **desativado recusa ENTRADA** (manual e por recebimento) | `EstoqueService.requireActiveForInbound` | `EstoqueServiceTest.adjustStock_entrada_emSkuDesativado_throwsAndDoesNotPersistAnything`, `adjustStock_entrada_emDepositoDesativado_throwsAndDoesNotPersistAnything` |
| Desativado **continua aceitando SAIDA e AJUSTE** — não prende saldo nem impede correção de inventário | `requireActiveForInbound` só age em `ENTRADA` | `EstoqueServiceTest.adjustStock_saida_emSkuDesativado_continuaPermitida`, `adjustStock_ajuste_emSkuDesativado_continuaPermitido` |
| SKU de variação só é "ativo" se a variação **e** o produto pai estiverem ativos | `ProductJpaRepository.isSkuActive` | `EstoqueRepositoryIT.isSkuActive_exigeProdutoPaiAtivo_inclusiveParaSkuDeVariacao` |
| SKU inexistente (404) tem precedência sobre SKU desativado (409) | ordem de `requireKnownSku` antes de `requireActiveForInbound` | `EstoqueServiceTest.adjustStock_entrada_skuDesconhecido_temPrecedenciaSobreDesativado` |
| Desativar não apaga: o SKU segue existindo, com histórico e saldo válidos | `active` é flag, não exclusão | `EstoqueRepositoryIT.isSkuActive_exigeProdutoPaiAtivo_inclusiveParaSkuDeVariacao` |
| `AJUSTE` é saldo-alvo: substitui o saldo, para cima ou para baixo, e aceita zero | `StockBalance.apply` | `StockBalanceTest.apply_ajuste_substituiOSaldoParaBaixo`, `apply_ajuste_paraZeroEhValido` |
| Baixar por `AJUSTE` nunca é `INSUFFICIENT_STOCK` — é substituição, não subtração | `StockBalance.apply` trata `AJUSTE` antes do cálculo de delta | `StockBalanceTest.apply_ajuste_abaixoDoSaldoAtual_naoLancaInsufficientStock` |
| Movimento de quantidade zero só é aceito em `AJUSTE` | `StockMovement` (compact constructor) | `StockMovementTest.ajuste_aceitaQuantidadeZero`, `saida_continuaRecusandoQuantidadeZero` |
| Só pode haver **um balanço aberto por depósito** | `EstoqueService.openStockCount` + `findOpenByWarehouseId` | `EstoqueServiceTest.openStockCount_recusaSegundoBalancoAbertoNoMesmoDeposito`, `EstoqueInventarioIT.balanco_segundoAbertoNoMesmoDeposito_returns_409` |
| Recontar um SKU sobrescreve, não cria segunda linha | `StockCount.withCountedItem` + `uk_stock_count_item_count_sku` | `StockCountTest.withCountedItem_recontarSobrescreveEPreservaAPosicao`, `EstoqueRepositoryIT.stockCount_recontagemNaoDuplicaLinhaDoMesmoSku` |
| Fechar aplica `AJUSTE` **só nos itens divergentes** — contagem que bateu não polui o ledger | `EstoqueService.closeStockCount` + `StockCountItem.diverges` | `EstoqueServiceTest.closeStockCount_aplicaAjusteApenasNosItensDivergentes`, `EstoqueInventarioIT.balanco_ajustaApenasOsDivergentesEDeixaTrilhaNoLedger` |
| A divergência fica gravada (`expectedQuantity`/`difference`), não só o saldo corrigido | `StockCountItem.reconciledWith` persistido no fechamento | `EstoqueRepositoryIT.stockCount_fechamentoPersisteExpectedEDifference` |
| SKU nunca movimentado é confrontado contra saldo zero | `closeStockCount` (`orElse(BigDecimal.ZERO)`) | `EstoqueServiceTest.closeStockCount_skuSemSaldoRegistrado_confrontaContraZero` |
| Fechar duas vezes é 409, não ajuste em dobro | `requireOpenStockCount` | `EstoqueServiceTest.closeStockCount_balancoJaFechado_throwsAndDoesNotAdjust`, `EstoqueInventarioIT.balanco_fecharDuasVezes_returns_409` |
| Cancelar não toca em saldo e libera o depósito para novo balanço | `StockCount.cancelled()` | `EstoqueServiceTest.cancelStockCount_naoTocaEmSaldo`, `EstoqueInventarioIT.balanco_cancelado_naoAjustaSaldoELiberaODeposito` |
| Contar SKU fora do catálogo é 404 na hora, não no fechamento | `recordCountedItem` → `requireKnownSku` | `EstoqueServiceTest.recordCountedItem_throwsWhenSkuNotInCatalog`, `EstoqueInventarioIT.balanco_contarSkuForaDoCatalogo_returns_404` |
| Movimentos com o mesmo `created_at` têm ordem determinística e paginação estável | desempate por `id` (BIGSERIAL) na ordenação do ledger | `EstoqueRepositoryIT.stockMovement_paginaDoMaisRecenteParaOMaisAntigo`, `stockMovement_paginacaoNaoRepeteNemPulaLinhaComCreatedAtIgual` |
| Consultar histórico de par SKU/depósito nunca movimentado devolve página vazia (200), não 404 | `EstoqueService.listMovements` | `EstoqueServiceTest.listMovements_returnsEmptyPageWhenSkuNeverMoved`, `EstoqueControllerTest.listMovements_returns_200_withEmptyPageWhenNeverMoved` |
| Histórico em depósito inexistente lança `WarehouseNotFoundException` (404) sem tocar no repositório de movimentações | `EstoqueService.listMovements` | `EstoqueServiceTest.listMovements_throwsWhenWarehouseNotFound` |
| `sku` e `warehouseCode` ausentes na query devolvem 400 `MISSING_PARAMETER` (não 500) | `GlobalExceptionHandler.handleMissingParam` | `EstoqueControllerTest.listMovements_withoutSku_returns_400`, `listMovements_withoutWarehouseCode_returns_400`, `GlobalExceptionHandlerTest.missingRequestParameter_returns400_namingTheParameter` |
| SKU órfão é diagnosticado por par SKU/depósito, considerando SKU pai **e** de variação como conhecidos | `StockIntegrityJpaRepository.findOrphanSkus` (anti-join `NOT EXISTS` contra `product` e `product_variant`) | `EstoqueRepositoryIT.orphanSkus_naoAcusaSkuPaiNemSkuDeVariacaoCadastrados`, `orphanSkus_acusaSkuForaDoCatalogoComSaldo` |
| Par presente nas três tabelas de estoque vira **uma** linha do diagnóstico, não três | `UNION` (não `UNION ALL`) na origem da query | `EstoqueRepositoryIT.orphanSkus_naoDuplicaQuandoOParEstaNasTresTabelas` |
| O diagnóstico de integridade é somente leitura — nenhum expurgo automático | `StockIntegrityRepository` sem operação de escrita | `EstoqueServiceTest.listOrphanSkus_doesNotTouchAnyWriteRepository` |

## API — Endpoints

Todos exigem `bearerAuth`. Controller: `adapter/in/controller/EstoqueController.java`.

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/estoque/products` | `ESTOQUE_PRODUCT_READ` | Lista produtos paginados (`page` = 0, `size` = 20, teto de 100) |
| `POST` | `/estoque/products` | `ESTOQUE_PRODUCT_MANAGE` (+ `ESTOQUE_PRODUCT_PRICE_MANAGE` se enviar `pricing`) | Cria produto (SKU pai) com variações, atributos e `pricing` opcional. `201` + `Location: /estoque/products/{sku}`; `409 SKU_ALREADY_EXISTS` |
| `PATCH` | `/estoque/products/{sku}` | `ESTOQUE_PRODUCT_MANAGE` (+ `ESTOQUE_PRODUCT_PRICE_MANAGE` se enviar `pricing`) | Altera `name`, `category` e/ou `pricing`. Campo ausente é mantido, inclusive dentro de `pricing`; não altera SKU nem variações. `200`; `404 PRODUCT_NOT_FOUND` |
| `GET` | `/estoque/products/{sku}/price` | `ESTOQUE_PRODUCT_READ` | Precificação vigente do SKU, com os derivados calculados. Aceita SKU **pai ou de variação**. `200`; `404 PRODUCT_NOT_FOUND` |
| `PATCH` | `/estoque/products/{sku}/active` | `ESTOQUE_PRODUCT_MANAGE` | Ativa/desativa o produto (`{"active": false}`). `200`; `404 PRODUCT_NOT_FOUND`; `400` se `active` ausente |
| `POST` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_MANAGE` | Cria depósito (`LOJA_FISICA` ou `ECOMMERCE`). `201` + `Location`; `409 WAREHOUSE_CODE_ALREADY_EXISTS` |
| `PATCH` | `/estoque/warehouses/{code}` | `ESTOQUE_WAREHOUSE_MANAGE` | Altera `name` e/ou `type`. Campo ausente é mantido; não altera o código. `200`; `404 WAREHOUSE_NOT_FOUND` |
| `PATCH` | `/estoque/warehouses/{code}/active` | `ESTOQUE_WAREHOUSE_MANAGE` | Ativa/desativa o depósito. `200`; `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/warehouses` | `ESTOQUE_WAREHOUSE_READ` | Lista depósitos paginados, ordenados por id (`page` = 0, `size` = 20, faixa 1–100) |
| `GET` | `/estoque/stock-balance` | `ESTOQUE_WAREHOUSE_READ` | Consulta saldo por `sku` + `warehouseCode`. Retorna zero se nunca houve movimentação; `404 WAREHOUSE_NOT_FOUND`; `400 VALIDATION_ERROR` |
| `POST` | `/estoque/movements` | `ESTOQUE_STOCK_MANAGE` | Registra movimentação manual (`ENTRADA`/`SAIDA`/`AJUSTE`) e devolve o saldo atualizado. `201` + `Location` para o saldo; `400 INSUFFICIENT_STOCK`; `404 WAREHOUSE_NOT_FOUND`; `409 STOCK_UPDATE_CONFLICT` |
| `GET` | `/estoque/movements` | `ESTOQUE_STOCK_MANAGE` | Histórico paginado do ledger por `sku` + `warehouseCode` (`page` = 0, `size` = 20, teto de 100), mais recentes primeiro. Par nunca movimentado devolve página vazia com `200`; `404 WAREHOUSE_NOT_FOUND`; `400 MISSING_PARAMETER` |
| `PUT` | `/estoque/products/{sku}/reorder-point` | `ESTOQUE_STOCK_MANAGE` | Define a quantidade mínima do SKU no depósito (upsert). `204 No Content`; `404 WAREHOUSE_NOT_FOUND` |
| `POST` | `/estoque/stock-counts` | `ESTOQUE_STOCK_MANAGE` | Abre um balanço para o depósito. `201` + `Location`; `404 WAREHOUSE_NOT_FOUND`; `409 STOCK_COUNT_ALREADY_OPEN` |
| `POST` | `/estoque/stock-counts/{id}/items` | `ESTOQUE_STOCK_MANAGE` | Registra a contagem física de um SKU (upsert por SKU; zero é válido). `200`; `404 PRODUCT_NOT_FOUND`/`STOCK_COUNT_NOT_FOUND`; `409 STOCK_COUNT_NOT_OPEN` |
| `POST` | `/estoque/stock-counts/{id}/close` | `ESTOQUE_STOCK_MANAGE` | Fecha e aplica os `AJUSTE` dos itens divergentes. `200`; `409 STOCK_COUNT_NOT_OPEN` |
| `POST` | `/estoque/stock-counts/{id}/cancel` | `ESTOQUE_STOCK_MANAGE` | Abandona o balanço sem tocar em saldo. `200`; `409 STOCK_COUNT_NOT_OPEN` |
| `GET` | `/estoque/stock-counts/{id}` | `ESTOQUE_STOCK_MANAGE` | Consulta o balanço e seus itens. `200`; `404 STOCK_COUNT_NOT_FOUND` |
| `GET` | `/estoque/stock-counts` | `ESTOQUE_STOCK_MANAGE` | Balanços do depósito por `warehouseCode`, mais recentes primeiro (`page`/`size` 1–100) |
| `GET` | `/estoque/integrity/orphan-skus` | `ESTOQUE_STOCK_MANAGE` | Diagnóstico de EST-C011: pares SKU/depósito com saldo, movimentações ou ponto de reposição gravados cujo SKU não existe no catálogo (`page` = 0, `size` = 20, teto de 100). Base íntegra devolve página vazia com `200` |
| `GET` | `/estoque/reservations` | `ESTOQUE_RESERVATION_READ` | Lista reservas de estoque paginadas, mais recentes primeiro. Filtros opcionais `sku`, `warehouseCode` e `status` (`ACTIVE`/`CONSUMED`/`RELEASED`/`EXPIRED`), combináveis. `404 WAREHOUSE_NOT_FOUND` se `warehouseCode` for informado e não existir |
| `GET` | `/estoque/reservations/{id}` | `ESTOQUE_RESERVATION_READ` | Consulta uma reserva. `404 RESERVATION_NOT_FOUND` |
| `GET` | `/estoque/integrity/reservation-mismatch` | `ESTOQUE_STOCK_MANAGE` | Diagnóstico de EST-C013: pares SKU/depósito cujo `stock_balance.reserved_quantity` diverge da soma das reservas `ACTIVE` em `stock_reservation` — estoque travado invisível, não overselling (`page` = 0, `size` = 20, teto de 100). Base íntegra devolve página vazia com `200` |

## Segurança e Infraestrutura

> Mecanismos transversais (JWT, filtros, CORS, headers, rate limit de login, lockout) estão em
> [`docs/security.md`](../../security.md); ambientes, containers e datastores em
> [`docs/infrastructure.md`](../../infrastructure.md); o modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura). Aqui fica só o recorte
> deste domínio.

### Permissões RBAC

| Permissão | Libera | Migration | Semeada em `dev`? |
|---|---|---|---|
| `ESTOQUE_PRODUCT_READ` | `GET /estoque/products` | V45 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `ESTOQUE_PRODUCT_MANAGE` | `POST /estoque/products` | V45 | ✅ |
| `ESTOQUE_PRODUCT_PRICE_MANAGE` | O bloco `pricing` no `POST`/`PATCH` de produto | V63 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `ESTOQUE_WAREHOUSE_READ` | `GET /estoque/warehouses`, `GET /estoque/stock-balance` | V47 | ✅ |
| `ESTOQUE_WAREHOUSE_MANAGE` | `POST /estoque/warehouses` | V47 | ✅ |
| `ESTOQUE_STOCK_MANAGE` | `POST`/`GET /estoque/movements`, `PUT .../reorder-point` | V56 | ✅ |

Concedidas a `ROLE_ADMIN` pelas migrations (`hml`/`prod`) e a `ROLE_ADMIN`/`ROLE_DEV` em runtime
por `SeedConfig`/`DevRoleBootstrapConfig` — necessário porque `dev` não roda Flyway. V45 e V47
inserem **sem** `ON CONFLICT DO NOTHING` (EST-C006).

**Por que o histórico exige `ESTOQUE_STOCK_MANAGE` e não `ESTOQUE_WAREHOUSE_READ`:** o ledger
carrega o `username` de quem realizou cada movimentação. Quem só precisa saber *quanto* existe
usa `GET /estoque/stock-balance` (`WAREHOUSE_READ`); ver *quem* mexeu é privilégio de quem
gerencia estoque. `EstoqueControllerSecurityTest.list_movements_with_warehouse_read_only_returns_403`
fixa essa decisão.

As escritas vindas de Compras e PDV **não passam por `@PreAuthorize` de estoque** — elas entram
por `EstoqueUseCase.adjustStock`, chamado de dentro de `ComprasService`/`PdvService`. Quem tem
`COMPRAS_RECEIPT_MANAGE` ou `PDV_SALE_MANAGE` movimenta saldo sem ter nenhuma permissão
`ESTOQUE_*`. É intencional (o port é a fronteira do domínio), mas significa que a permissão de
estoque não é o único caminho para alterar saldo.

### Rate limiting

❌ **Nenhum endpoint deste módulo é limitado.** O `LoginRateLimitingFilter`
(`infra/security/LoginRateLimitingFilter.java:42-77`) cobre apenas `/auth/**` e duas rotas de
notificação. `GET /estoque/movements` pode ser varrido em loop por qualquer token válido com
`ESTOQUE_STOCK_MANAGE`. Ver PLAT-C030.

### Isolamento de dados

Sistema single-tenant: quem tem `ESTOQUE_WAREHOUSE_READ` enxerga **todos** os depósitos, e quem
tem `ESTOQUE_STOCK_MANAGE` movimenta **qualquer** SKU em **qualquer** depósito. Não existe
vínculo usuário↔depósito — é a limitação a resolver antes de operar com mais de uma loja.

### Auditoria

Toda operação que altera saldo publica `AuditEvent`:

| Operação | Controller | `EventType` |
|---|---|---|
| `POST /estoque/products` | `EstoqueController` | `PRODUCT_CREATED` |
| `PATCH /estoque/products/{sku}` | `EstoqueController` | `PRODUCT_UPDATED` (+ `PRODUCT_PRICE_CHANGED` quando o corpo traz `pricing`) |
| `PATCH /estoque/products/{sku}/active` | `EstoqueController` | `PRODUCT_ACTIVATED` / `PRODUCT_DEACTIVATED` |
| `POST /estoque/warehouses` | `EstoqueController` | `WAREHOUSE_CREATED` |
| `PATCH /estoque/warehouses/{code}` | `EstoqueController` | `WAREHOUSE_UPDATED` |
| `PATCH /estoque/warehouses/{code}/active` | `EstoqueController` | `WAREHOUSE_ACTIVATED` / `WAREHOUSE_DEACTIVATED` |
| `POST /estoque/stock-counts` | `EstoqueController` | `STOCK_COUNT_OPENED` |
| `POST /estoque/stock-counts/{id}/close` | `EstoqueController` | `STOCK_COUNT_CLOSED` (com `itemCount` e `divergentCount`) |
| `POST /estoque/stock-counts/{id}/cancel` | `EstoqueController` | `STOCK_COUNT_CANCELLED` |
| `POST /estoque/movements` | `EstoqueController` | `STOCK_MOVEMENT_REGISTERED` |
| `PUT /estoque/products/{sku}/reorder-point` | `EstoqueController` | `REORDER_POINT_SET` |
| `POST /pdv/sessions/{id}/sales` | `PdvController` | `STOCK_MOVEMENT_REGISTERED` (`origin: PDV_SALE`) |
| `POST /compras/goods-receipts` | `ComprasController` | `STOCK_MOVEMENT_REGISTERED` (`origin: GOODS_RECEIPT`) |

Venda e recebimento emitem **um evento por operação**, não por item, com os campos `origin`,
`warehouseCode`, `type`, `skus` e `itemCount` — o detalhamento item a item continua no ledger
`stock_movement`. A publicação fica nos controllers (adapter), e não nos services, porque
`HexagonalArchitectureTest` só libera `org.springframework.transaction.*` dentro de `core/service`.

Leituras (incluindo `GET /estoque/movements`) não geram evento.

Retenção dos `audit_logs`: 365 dias (`AuditLogCleanupService`); leitura por `GET /audit-logs`
com `AUDIT_READ`.

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `product`, `product_variant`, `warehouse`, `stock_balance`, `stock_movement`, `stock_reorder_point` | módulo indisponível |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior, sem perda de função |
| `UserRepository.findUsernamesByPermission` | destinatários do alerta de reposição | alerta não sai |
| `NotificationUseCase` + SSE (`SseEmitterRegistry`) | entrega do alerta de ponto de reposição | notificação fica só no banco |
| Optimistic locking (`@Version` em `stock_balance`) | protege o saldo sob escrita concorrente | — |

O alerta roda **dentro** da transação de escrita, o que prolonga a transação e gera uma
notificação por item (EST-C003). Não há fila: se a entrega falhar, não há retry.

### Limites operacionais

- Os quatro endpoints paginados (`/products`, `/warehouses`, `/movements`,
  `/integrity/orphan-skus`): `page >= 0` e `size` entre 1 e 100, ambos por Bean Validation.
  `size` fora da faixa é **400 `VALIDATION_ERROR`**, não um teto silencioso (EST-C005).
- `sku` (3–50) e `warehouseCode` (2–50) em query e path são validados com `@NotBlank`/`@Size`,
  espelhando as constraints dos DTOs de escrita.
- Sem upload de arquivo neste módulo. A importação de XML de NF-e (EST-F005) vai introduzir o
  primeiro — e vai precisar de limite de tamanho e validação de conteúdo próprios.

### Riscos conhecidos

- **PLAT-C030** — sem rate limit em nenhum endpoint do módulo, inclusive
  `GET /estoque/integrity/orphan-skus`, cuja query nativa é a mais cara do módulo.
- O passivo de SKU órfão anterior a EST-C002 **continua na base até alguém decidir o destino de
  cada SKU**. `GET /estoque/integrity/orphan-skus` e
  [`scripts/estoque-orphan-skus.sql`](../../../scripts/estoque-orphan-skus.sql) levantam a lista;
  a limpeza é manual, por decisão (ver EST-C011 no Histórico).

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

Antes de qualquer escrita, `adjustStock` e `setReorderPoint` exigem que o SKU exista no catálogo
— como SKU pai ou como SKU de variação (`ProductRepository.existsBySku`). Vale para as três
portas de entrada: movimentação manual, venda e recebimento. SKU desconhecido responde 404
`PRODUCT_NOT_FOUND` e reverte a operação inteira, em vez de criar saldo órfão (EST-C002).

O alerta de reposição (`notifyIfBelowReorderPoint`) **acumula** os SKUs que cruzaram o mínimo
durante a operação e despacha **uma notificação por destinatário depois do commit**, via o port
`AfterCommitExecutor` (implementado em `infra/transaction/TransactionAfterCommitExecutor`). Assim
uma venda com N itens abaixo do mínimo gera um aviso listando os N SKUs — não N avisos —, a
transação de venda não espera o envio, e uma venda revertida não notifica ninguém (EST-C003).

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

**V62 — `estoque_stock_count`**
- `stock_count` (id, warehouse_id FK → `warehouse` ON DELETE CASCADE, status VARCHAR(20) [`ABERTA`|`FECHADA`|`CANCELADA`], username, created_at, closed_at NULL) — índice `idx_stock_count_warehouse_status`
- `stock_count_item` (id, stock_count_id FK → `stock_count` ON DELETE CASCADE, sku, counted_quantity NUMERIC(14,3), expected_quantity NULL, difference NULL) — UNIQUE `uk_stock_count_item_count_sku (stock_count_id, sku)`; índice `idx_stock_count_item_count_id`
- **Sem permissão nova:** o balanço reusa `ESTOQUE_STOCK_MANAGE`, a mesma que já autoriza movimentar saldo — fechar uma contagem é exatamente isso, em lote.

**V61 — `stock_reorder_points`**
- `stock_reorder_point` (id, sku, warehouse_id FK → `warehouse` ON DELETE CASCADE, min_quantity NUMERIC(14,3)) — UNIQUE `uk_stock_reorder_point_sku_warehouse (sku, warehouse_id)`

**V63 — `estoque_product_pricing`**
- `product` ganha `cost_price NUMERIC(14,2)`, `markup_percent NUMERIC(9,4)` e `sale_price NUMERIC(14,2)`, todas **NULLABLE** — preço desconhecido não é preço zero, e um `DEFAULT 0` faria o PDV vender de graça em vez de recusar item sem preço. Sem backfill: todo produto já cadastrado passa a existir como não precificado.
- CHECKs `ck_product_cost_price_non_negative`, `ck_product_markup_percent_non_negative` e `ck_product_sale_price_non_negative`, espelhando as invariantes de `Pricing` — redundantes por desenho, porque o schema é a barreira que sobrevive a carga direta e import de planilha.
- Escala **4** em `markup_percent` (e não 2) porque markup é input de fórmula, não valor de exibição: 33,3333% sobre custo 45,00 tem que reproduzir 60,00 no preço sugerido, e centavo errado em preço de prateleira vira divergência de caixa.
- Cria `ESTOQUE_PRODUCT_PRICE_MANAGE` para `ROLE_ADMIN`, com `ON CONFLICT DO NOTHING`.
- **`product_variant` não recebe colunas de preço** — a variação herda o preço do pai. Ver EST-F020.

**Nota de modelagem:** `stock_balance`, `stock_movement` e `stock_reorder_point` referenciam
`warehouse(id)` por FK, mas guardam `sku` como **texto livre** — não há FK para `product.sku`
nem para `product_variant.sku`. Ver EST-C002.

## Cobertura de Testes

| Arquivo | Tipo | O que cobre |
|---|---|---|
| `core/service/EstoqueServiceTest` | Unit (Mockito) | Todos os casos de uso, incluindo os 3 cenários de alerta de reposição, os 3 de histórico de movimentações e os 10 de precificação (EST-F019: PATCH parcial que não apaga campo, herança de preço pai→variação, SKU fora do catálogo) |
| `core/domain/model/estoque/StockBalanceTest` | Unit de domínio | `zero`/`of`, invariantes, e os 5 cenários de `apply` (entrada, saída, drenar a zero, insuficiente, ajuste) |
| `core/domain/model/estoque/StockMovementTest` | Unit de domínio | `create`/`of` e todas as invariantes |
| `core/domain/model/estoque/WarehouseTest` | Unit de domínio | `create`/`of`, obrigatoriedade de code/name/type e os `with*` de EST-F018 |
| `adapter/in/controller/EstoqueControllerTest` | MockMvc standalone | 29 casos: 200/201/204/400/404/409 dos 8 endpoints |
| `adapter/in/controller/EstoqueControllerValidationTest` | `@SpringBootTest` + MockMvc real | Bean Validation dos `@RequestParam`/`@PathVariable`: faixa de `page`/`size` nos 4 endpoints paginados, `sku`/`warehouseCode` em branco ou fora do tamanho, e a distinção entre `VALIDATION_ERROR` e `MISSING_PARAMETER`. **Não é standalone de propósito** — a validação de parâmetro de handler é aplicada pelo `RequestMappingHandlerAdapter`, não pelo controller |
| `adapter/in/controller/EstoqueControllerSecurityTest` | MockMvc + Security | 401 sem auth / 403 sem authority / sucesso com a authority correta, endpoint a endpoint — inclui o 403 de `WAREHOUSE_READ` no histórico de movimentações |
| `infra/config/DevRoleBootstrapConfigTest` | Unit (Mockito) | 4 casos: `ROLE_DEV` recebe as permissões de negócio (incl. `PDV_SALE_MANAGE`), as `DEV_ONLY_*`, e não cria usuário sem `DEV_EMAIL` |
| `infra/config/SeedConfigTest` | Unit (Mockito) | `ROLE_ADMIN` recebe as permissões `ESTOQUE_*` e `PDV_SALE_MANAGE` no seed de dev |
| `adapter/in/controller/EstoqueAlertaIT` | `@SpringBootTest` (profile `dev`) | E2E do alerta: depósito → ENTRADA 20 → mínimo 10 → SAIDA 12 → notificação em `GET /notifications` |
| `core/service/ComprasServiceTest` | Unit | Recebimento ajusta estoque por item; falha do estoque (saldo, depósito ou SKU desconhecido) propaga e não salva o receipt |
| `core/service/PdvServiceTest` | Unit | Venda dá baixa por item; `InsufficientStockException` e `ProductNotFoundException` revertem a venda inteira |
| `core/service/StockBalanceConcurrencyIT` | `@SpringBootTest` (profile `dev`) | 8 escritas simultâneas no mesmo saldo: sem lost update, conflitos tratados; idem na primeira movimentação do par |
| `adapter/out/persistence/repository/EstoqueRepositoryIT` | `@SpringBootTest` + `@Transactional` | Os 6 `*RepositoryImpl`: round-trip de produto com variações/atributos, `existsBySku` em SKU pai e de variação, paginação ID-first, propagação do `version`, ordem do ledger, upsert do ponto de reposição, e os 7 cenários da query nativa de SKU órfão |
| `infra/transaction/TransactionAfterCommitExecutorTest` | Unit | Agregação por chave, despacho único no commit, silêncio no rollback, isolamento entre transações da mesma thread, falha de um lote não derruba o próximo |
| `core/domain/model/estoque/ProductTest` | Unit de domínio | `create`/`of`, cópia defensiva das variações, e os `with*` de EST-F018: semântica de "null = manter", preservação de sku/active/variações e invariantes que continuam valendo |
| `core/domain/model/estoque/PricingTest` | Unit de domínio | EST-F019, 35 casos em 6 grupos: invariantes de não-negatividade, preço sugerido (incl. arredondamento HALF_UP e as 4 casas do markup), precedência do preço praticado sobre o sugerido, markup × margem (o caso custo 50 / venda 100 = 100% e 50%), divisões indefinidas devolvendo `null`, venda abaixo do custo, PATCH parcial e `materializeSuggestion` |
| `core/domain/model/estoque/StockCountTest` | Unit de domínio | Ciclo de vida do balanço, upsert de item preservando posição e id, contagem zero, `closed()`/`cancelled()` |
| `core/domain/model/estoque/StockCountItemTest` | Unit de domínio | `reconciledWith` (falta, sobra e contagem que bateu) e `diverges()` |
| `adapter/in/controller/EstoqueInventarioIT` | `@SpringBootTest` (profile `dev`) | E2E do balanço: abrir → contar 3 SKUs → fechar → conferir saldo e ledger; contagem zero, cancelamento, duplo fechamento, recontagem e SKU fora do catálogo |
| `core/domain/model/estoque/OrphanSkuTest` | Unit de domínio | Invariantes do retrato de diagnóstico: obrigatoriedade de `sku`/`warehouseCode`, `quantity` nula vira zero, `movementCount` não-negativo, `lastMovementAt` nulo permitido |

**Lacunas conhecidas:** o histórico de movimentações tem cobertura de service, controller e
segurança, mas ainda não tem um IT end-to-end que grave movimentações reais e as releia pelo
endpoint. `EstoqueRepositoryIT` roda contra H2 em modo PostgreSQL, como os demais ITs
do projeto — divergências específicas do Postgres continuam fora de cobertura automatizada.

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
| `03 — Saldo e movimentações` | saldo zerado inicial, `ENTRADA` → `SAIDA` → `AJUSTE` conferindo o saldo a cada passo (o `AJUSTE` confere o **saldo-alvo**, não a soma), entrada no SKU de variação, e os erros `INSUFFICIENT_STOCK`, quantidade negativa, depósito inexistente e `PRODUCT_NOT_FOUND` |
| `04 — Ponto de reposição e alerta` | upsert do mínimo, saída que **não** cruza o mínimo, saída que cruza, a conferência da notificação em `GET /notifications` e o `PRODUCT_NOT_FOUND` do mínimo em SKU fora do catálogo |
| `05 — Segurança` | 401 sem token e com token inválido |
| `06 — Integridade` | levantamento de SKU órfão conferindo que os SKUs cadastrados pela própria coleção **não** são acusados, o 400 de `size` acima do teto e o 401 sem token |
| `08 — Balanço de inventário` | ciclo completo num depósito próprio: abrir, o 409 do segundo balanço, contar, recontagem que sobrescreve, SKU fora do catálogo, fechar conferindo `expectedQuantity`/`difference`, o saldo ajustado, o 409 do duplo fechamento e a listagem |
| `07 — Edição e desativação` | PATCH parcial de produto e depósito (nome muda, categoria/tipo e SKU/código ficam), corpo vazio como no-op, os 400 de validação, e o ciclo desativar → `ENTRADA` 409 → `SAIDA` 201 → reativar |

O SKU e o código de depósito são gerados com timestamp a cada execução, então a coleção é
reexecutável sem limpeza manual.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| EST-F005 | 🟡 Média | Feature | importacao-nfe-xml | Entrada de mercadoria por XML de NF-e (`NfeXmlImportPort`) gerando `StockMovement` de entrada — diferencial operacional. | Backlog (Sprint 4) |
| EST-F007 | 🟡 Média | Feature | valorizacao-custo-medio | Custo médio ponderado por SKU e valor total de estoque — alimenta o DRE do domínio `financeiro`. **Não fazer antes do cashback** ([`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §8.9): o `costPrice` manual de V63 já entrega a ordem de grandeza, e é a ordem de grandeza que decide se a taxa do carvão é 2% ou 8%. Depois do cashback rodando, o custo médio refina; antes, ele atrasa. | Backlog (Sprint 3) |
| EST-F008 | 🟡 Média | Feature | controle-lote-validade | Lote e validade para essências/perecíveis + alerta de vencimento próximo. | Backlog (Sprint 3) |
| EST-F011 | 🟢 Baixa | Feature | curva-abc-giro | Análise ABC e giro de produtos para priorização de compras (domínio `relatorios`). | Backlog (Sprint 6) |
| EST-F012 | 🟢 Baixa | Feature | transferencia-entre-depositos | `MovementType.TRANSFER`: saída atômica de um `Warehouse` + entrada em outro, distinto do ajuste manual. **Só faz sentido quando existir um segundo local físico de verdade** ([`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.2): o marketplace **não** vai usar `WarehouseType.ECOMMERCE` para separar canal — para uma tabacaria de uma loja, a prateleira é uma só, e partir o pool geraria rebalanceamento manual permanente e o absurdo de "o site tem 5 e a loja tem 0" com tudo no mesmo armário. A reserva (EST-F013) é o mecanismo que permite um pool servir dois canais. | Backlog (Sprint 4) |
| EST-F016 | 🟢 Baixa | Feature | unidade-medida-conversao | Múltiplas unidades por produto (compra em kg, venda em porção/g) com fator de conversão nas movimentações. | Backlog (Sprint 6) |
| EST-F020 | 🟢 Baixa | Feature | preco-por-variacao | Preço no SKU de variação, hoje herdado do pai (EST-F019). Necessário quando a grade tem preços distintos (tamanhos de narguilé), não quando só muda o sabor. Exige decidir a precedência variação → pai e propagar em `findPricingBySku`. **Desaconselhado por ora** ([`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §8.5): para a tabacaria, sabores da mesma essência custam o mesmo; grade com preços distintos se modela como produtos separados até doer. | Pendente |
| EST-C006 | 🟢 Melhoria | Correção | migrations-v45-v47-sem-on-conflict | V45 e V47 inserem permissões sem `ON CONFLICT DO NOTHING`, ao contrário de V56/V57/V60. Re-execução em base parcialmente populada quebra. Herdado do antigo C018. | Pendente |

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

- **2026-07-27** — `validar-existencia-do-sku` (EST-C002): novo `ProductRepository.existsBySku`, resolvido por uma consulta só que cobre SKU pai e SKU de variação (`ProductJpaRepository.existsBySkuOrVariantSku`, apoiada nos índices únicos já existentes da V44 — sem migration). `adjustStock` e `setReorderPoint` passam a exigir SKU conhecido antes de qualquer escrita, lançando `ProductNotFoundException` → 404 `PRODUCT_NOT_FOUND`. Cobre as três portas de escrita de uma vez: movimentação manual, venda no PDV e recebimento em Compras. Optou-se por validação na aplicação em vez de FK no banco, porque `stock_movement` é histórico imutável e uma FK impediria arquivar ou renomear produto. O passivo de saldo órfão anterior à correção ficou registrado como EST-C011.
- **2026-07-27** — `violacao-de-constraint-retornava-500` (EST-C010): `createProduct` só checava o SKU pai, então SKU de variação duplicado batia em `uk_product_variant_sku` e virava 500 com a mensagem do driver no corpo — o javadoc de `EstoqueUseCase` já prometia 409 desde EST-F001. Agora valida SKU pai e de variações (inclusive repetição dentro do próprio payload). Somado a isso, handler de rede de segurança para `DataIntegrityViolationException` → 409 `DATA_INTEGRITY_VIOLATION` com mensagem genérica, que também cobre a corrida de primeira movimentação simultânea do mesmo par SKU/depósito (sem linha anterior não há `version` para conferir; quem protege é a unique constraint).
- **2026-07-27** — `notificacao-reposicao-em-loop-e-na-transacao` (EST-C003): novo port `AfterCommitExecutor` (`core/ports/out`) com implementação em `infra/transaction/TransactionAfterCommitExecutor` sobre `TransactionSynchronizationManager`. O alerta passa a ser acumulado durante a operação e despachado uma única vez após o commit: venda com N SKUs abaixo do mínimo gera um aviso listando os N, a transação de venda não espera o envio, e venda revertida não notifica ninguém. Cobertura em `TransactionAfterCommitExecutorTest` (agregação, commit, rollback, isolamento entre transações da mesma thread).
- **2026-07-27** — `audit-event-ausente-em-venda-e-recebimento` (EST-C004): `PdvController` e `ComprasController` passam a publicar `STOCK_MOVEMENT_REGISTERED`, um evento por operação com `origin`, `warehouseCode`, `type`, `skus` e `itemCount`. A publicação ficou nos controllers porque `HexagonalArchitectureTest` barra `ApplicationEventPublisher` em `core/service`. Fecha também as contrapartes COM-C003 e PDV-C003.
- **2026-07-27** — `lacunas-de-teste-persistencia-e-concorrencia` (EST-C007): `StockBalanceConcurrencyIT` prova que o `@Version` de `stock_balance` impede lost update sob 8 escritas simultâneas (saldo final == baixas confirmadas) e que o perdedor da corrida vira conflito tratado, não 500; cobre também a corrida de primeira movimentação. `EstoqueRepositoryIT` cobre os cinco `*RepositoryImpl` do módulo — round-trip de produto com variações e atributos, `existsBySku` achando SKU pai e de variação, paginação ID-first, propagação do `version`, ordem do ledger e upsert do ponto de reposição.
- **2026-07-27** — `package-info-obsoletos` (EST-C008): os `package-info` de `core/domain/model/estoque` e `core/ports/out/estoque` descreviam o módulo como "esqueleto (TODO)" e listavam como previstos modelos e adapters existentes desde EST-F001/F002; o comentário equivalente em `CoreBeanConfig` também foi corrigido. O único TODO que sobrou é o `NfeXmlImportPort` (EST-F005).

- **2026-07-27** — `inventario-contagem` + `ajuste-de-inventario-so-incrementa` (EST-F006 + EST-C009, feitos juntos porque a semântica de `AJUSTE` era a modelagem do balanço): **EST-C009** — `StockBalance.apply` tratava tudo que não era `SAIDA` como soma, então `AJUSTE` só aumentava saldo e um acerto para baixo precisava virar `SAIDA` falsa, poluindo o ledger. `AJUSTE` passou a ser **saldo-alvo**: a quantidade é o valor contado na prateleira, o saldo passa a valer exatamente aquilo, e zero é alvo válido. O ledger continua replayável — um `AJUSTE` grava o alvo, e reaplicá-lo dá o mesmo resultado. `StockMovement` agora aceita `quantity == 0` **só** em `AJUSTE`, e o `@DecimalMin` do request virou inclusivo (zero em `ENTRADA`/`SAIDA` continua barrado, pelo invariante do domínio → 400 `BAD_REQUEST`). PDV e Compras não foram afetados: usam `SAIDA` e `ENTRADA`, e nada no main consumia `AJUSTE`. **EST-F006** — `StockCount` como sessão de balanço por depósito (`ABERTA` → `FECHADA`/`CANCELADA`), com `StockCountItem` guardando o contado e, no fechamento, o `expectedQuantity` e a `difference`. Escolheu-se sessão em vez de ajuste avulso porque o balanço é o evento que a operação reconhece: dá para contar aos poucos, conferir antes de mexer no saldo, e auditar depois quem contou o quê e quanto faltava. Fechar aplica um `AJUSTE` por item **divergente** — contagem que bateu não gera movimentação —, tudo na mesma transação, e os alertas de ponto de reposição saem agregados após o commit (EST-C003). Um balanço aberto por depósito, porque dois simultâneos contariam o mesmo saldo e se sobrescreveriam. Não há estado "em contagem": uma contagem aberta já está sendo contada; `CANCELADA` resolve o caso real de abandonar o balanço sem aplicar nada. Migration V62; sem permissão nova — reusa `ESTOQUE_STOCK_MANAGE`, já que fechar um balanço é movimentar saldo em lote.
- **2026-07-27** — `atualizar-desativar-produto-deposito` (EST-F018): produto e depósito só tinham `create` e `list` — o campo `active` nascia `true` e nunca mudava. Agora há `PATCH /estoque/products/{sku}`, `PATCH /estoque/warehouses/{code}` e os respectivos `/active`. **PATCH parcial** (`null` = manter) em vez de `PUT`: não existe `GET` de produto por SKU, então um cliente não teria como ler o recurso inteiro antes de reescrevê-lo, e um `PUT` apagaria por omissão. `sku` e `code` ficaram fora da edição — são identidade, e o SKU em especial é referenciado como texto livre por `stock_balance`/`stock_movement`/`stock_reorder_point`: renomeá-lo transformaria todo o histórico do produto em órfão (EST-C011). As variações também ficaram fora, porque mexer na grade altera o espaço de nomes de SKU e exigiria a validação de duplicidade de `createProduct`. **Desativação em endpoint próprio**, não como campo do PATCH, para render `PRODUCT_DEACTIVATED`/`WAREHOUSE_DEACTIVATED` na auditoria em vez de se confundir com uma correção de nome; `active` é `Boolean` com `@NotNull`, para corpo vazio não virar um "desativar" silencioso vindo do default `false`. **Efeito no saldo:** desativado recusa `ENTRADA` (409 `PRODUCT_INACTIVE`/`WAREHOUSE_INACTIVE`) mas continua aceitando `SAIDA` — desativar quer dizer "não reponho mais", e bloquear a saída deixaria preso o saldo que ainda está na prateleira. `AJUSTE` também passa, porque é o caminho de correção de inventário. Novo `ProductRepository.isSkuActive`, que exige produto pai ativo inclusive para SKU de variação: desativar o pai tira a grade inteira de circulação de uma vez. Sem migration — as colunas `active` já existiam desde a V44/V46. Limitação conhecida: com `null` significando "manter", não há como **limpar** a `category`, só trocá-la.
- **2026-07-27** — `validacao-e-paginacao-nos-endpoints-de-leitura` (EST-C005): `EstoqueController` recebeu `@Validated` e os `@RequestParam`/`@PathVariable` ganharam constraints — `page >= 0`, `size` entre 1 e 100, `sku` 3–50 e `warehouseCode` 2–50, espelhando os DTOs de escrita. O `Math.min(size, 100)` silencioso saiu: `size` fora da faixa agora é **400 `VALIDATION_ERROR`**, alinhando `/estoque` com `/compras` e `/pdv`. `GET /estoque/warehouses` passou a ser paginado (`WarehouseRepository.findAll(page, size)` → `PageResult`, ordenado por `id`), o que é **mudança de contrato**: os depósitos saíram da raiz do JSON para `content`. Sem migration. Junto veio a correção de uma ponta de infra que valia para o projeto inteiro: desde o Spring Framework 6.1 a validação de parâmetro de handler é nativa do `RequestMappingHandlerAdapter` e lança `HandlerMethodValidationException`, não `ConstraintViolationException` — sem handler para ela, o `GlobalExceptionHandler` a jogava no catch-all de `Exception` e devolvia **500**. Era o comportamento real de `GET /compras/suppliers?size=200`, cujos `@Min`/`@Max` existiam desde COM-F001 e nunca tinham sido exercitados por teste. Nova `EstoqueControllerValidationTest` com contexto real (o standalone de `EstoqueControllerTest` não reproduz essa montagem).
- **2026-07-27** — `saldo-orfao-ja-existente-na-base` (EST-C011): EST-C002 fechou a porta para novos órfãos, mas o passivo anterior seguia invisível na base — e é ele que contaminaria os relatórios de EST-F006 e EST-F007. Entregue o **levantamento**, não a limpeza: novo port `StockIntegrityRepository` (`core/ports/out/estoque`), query nativa em `StockIntegrityJpaRepository` e `GET /estoque/integrity/orphan-skus` paginado sob `ESTOQUE_STOCK_MANAGE`, mais o script avulso [`scripts/estoque-orphan-skus.sql`](../../../scripts/estoque-orphan-skus.sql) para o caminho DBA. O retrato é o record `OrphanSku` — uma linha por par SKU/depósito, com saldo, contagem e data do último movimento e presença de ponto de reposição, que é o contexto de que a decisão humana precisa. **Nenhum expurgo automático, de propósito:** os dois destinos possíveis (cadastrar o produto que faltava × apagar a digitação errada) são incompatíveis e a consulta não os distingue, então apagar em massa destruiria histórico legítimo — o script traz o bloco de `DELETE` comentado, com lista de SKUs a preencher à mão. Query nativa porque a origem é o `UNION` de três tabelas e JPQL não tem `UNION`; sem migration, e sem permissão nova. Cobertura na `EstoqueRepositoryIT` (7 cenários, incluindo SKU de variação, órfão só com ledger e paginação estável).
- **2026-07-27** — `ordenacao-instavel-do-ledger` (EST-C012): o histórico ordenava só por `created_at DESC`, chave não-única — uma venda com N itens grava N movimentos no mesmo loop e na mesma transação, com `created_at` idêntico. Além da ordem de exibição arbitrária, a paginação de `GET /estoque/movements` ficava instável: com chave de ordenação não-única o banco não garante ordem consistente entre consultas, então a mesma linha podia voltar em duas páginas ou não aparecer em nenhuma. Corrigido com desempate por `id` (`findBySkuAndWarehouseIdOrderByCreatedAtDescIdDesc`); `id` é BIGSERIAL monotônico e dá ordem total. Sem migration — o índice `idx_stock_movement_sku_warehouse_created` continua servindo ao filtro e ao prefixo da ordenação. Achado ao escrever o `EstoqueRepositoryIT` do EST-C007, que reproduziu o cenário de venda multi-item.
- **2026-07-28** — `precificacao-de-produto` (EST-F019): o catálogo não tinha preço em lugar nenhum — `sale_item.unit_price` era o único valor monetário do estoque, **digitado no request de cada venda**, o que fazia o operador do PDV redigitar o preço a cada atendimento e impedia qualquer relatório de faturamento confiável. Entrou o value object `Pricing` (`costPrice`, `markupPercent`, `salePrice`, os três opcionais) embutido em `Product`, migration V63 e `GET /estoque/products/{sku}/price`. **Markup e margem são expostos separados de propósito:** markup é sobre o custo e é o input do lojista ("compro a 50 e quero 100% em cima"), margem é sobre a venda e é o que sobra — custo 50 e venda 100 são 100% de markup e 50% de margem, e confundi-los é o erro clássico de precificação de varejo. Por isso a margem também sai calculada: é ela, e não o faturamento, que dimensiona desconto e cashback. **`salePrice` vence sobre o sugerido** quando informado, para caber preço psicológico (R$ 49,90 em vez dos R$ 48,73 da fórmula); `effectiveMarkupPercent` revela o markup que o preço praticado realmente entrega, contra o pretendido que ficou guardado. Os derivados são calculados no backend e serializados no DTO em vez de deixados para a UI, que os recalcularia com outra regra de arredondamento e divergiria do caixa em centavos. Campos **NULLABLE sem backfill**: preço desconhecido não é preço zero, e um `DEFAULT 0` faria o PDV vender de graça em vez de recusar a venda. Derivado indefinido (custo ausente, divisão por zero) volta `null`, nunca zero — a ausência é informação. Venda abaixo do custo é **sinalizada, não bloqueada** (`belowCost`): queima de estoque e produto-isca são decisões comerciais legítimas. Nova permissão `ESTOQUE_PRODUCT_PRICE_MANAGE`, separada de `ESTOQUE_PRODUCT_MANAGE`, para que quem mantém o cadastro não ganhe de brinde o poder de mexer em preço — checada via SpEL no `@PreAuthorize` (`#request.pricing == null or hasAuthority(...)`), e só quando o corpo traz o bloco `pricing`. Auditoria própria `PRODUCT_PRICE_CHANGED`, para a pergunta "quem baixou o preço disso e quando" não se perder no meio dos `PRODUCT_UPDATED` de renomeação. **Preço mora no SKU pai** e a variação herda (`ProductRepository.findByAnySku`, o caminho do leitor de código de barras no balcão): sabores diferentes da mesma essência custam o mesmo, e preço por variação virou EST-F020. As assinaturas antigas de `createProduct`/`updateProduct` sobreviveram como `default` na interface, então nenhum chamador existente precisou mudar. Cobertura: `PricingTest` (35 casos de domínio, incluindo os arredondamentos e as divisões indefinidas), 10 casos novos em `EstoqueServiceTest`, 7 em `EstoqueControllerTest`, 4 de round-trip em `EstoqueRepositoryIT` e 7 de RBAC em `EstoqueControllerSecurityTest`.
- **2026-07-29** — `reserva-de-estoque-endpoint-scheduler-integridade` (EST-F013/EST-F021/EST-C013): a V64 e o núcleo em `EstoqueService` já existiam sem superfície HTTP nem varredor — `reserved_quantity` era uma coluna que ninguém alimentava pela API, então disponível e físico coincidiam por acidente, não por desenho. Fecha as três pontas que restavam. **Endpoint (F013):** `GET /estoque/reservations` (paginado, filtros opcionais `sku`/`warehouseCode`/`status`, com `warehouseCode` resolvido por depósito e cacheado por request) e `GET /estoque/reservations/{id}`, sob `ESTOQUE_RESERVATION_READ`. **Só leitura, de propósito:** criar, consumir e liberar reserva é orquestração interna — o checkout do marketplace (Fatia 9, ainda não existe) e a liquidação de pedido online no PDV (`consumeReservationsByOwner`, já em produção) — não uma operação que um humano dispara pelo Swagger, então não há `POST`/`{id}/release` aqui. **Scheduler (F021):** novo `StockReservationExpiryCleanupService` (`infra/scheduler`), `@Scheduled` a cada 5 minutos (não diário, como os demais `*CleanupService`) + `@SchedulerLock`, chamando `expireReservations` em lotes de 200 — o TTL padrão da reserva é 30 minutos, e uma varredura diária deixaria estoque travado por quase um dia após vencer. **Integridade (C013):** novo record `ReservationIntegrityMismatch` + `StockIntegrityRepository.findReservationMismatches`, query nativa em `StockIntegrityJpaRepository` no mesmo molde de EST-C011 (união dos candidatos de `stock_balance.reserved_quantity > 0` e `stock_reservation` `ACTIVE`, comparando os dois lados) e `GET /estoque/integrity/reservation-mismatch` sob `ESTOQUE_STOCK_MANAGE` — mesma régua de permissão do órfão de SKU, e 200 com página vazia quando a base está íntegra, não 404. `StockReservationNotFoundException`/`NotActiveException` ganharam handler em `GlobalExceptionHandler` (404 `RESERVATION_NOT_FOUND` / 409 `RESERVATION_NOT_ACTIVE`) — existiam desde a V64 sem nenhuma rota que as alcançasse. Sem migration nova; permissões `ESTOQUE_RESERVATION_READ`/`MANAGE` já vieram seedadas na V64. Cobertura: `ReservationIntegrityMismatchTest` (domínio), casos novos em `EstoqueControllerTest` e `EstoqueControllerSecurityTest`, e 4 cenários em `EstoqueRepositoryIT` para a query de integridade (contador acima do ledger, ledger acima do contador, os dois batendo, e reserva já resolvida saindo do cálculo). **Gap conhecido, não fechado aqui:** o domínio/service de reserva em si (`StockReservation`, os oito métodos de `EstoqueService`, `StockBalance.reserve/consumeReservation/releaseReservation`) segue sem suíte de teste própria — essa entrega só cobriu a query de integridade e a superfície nova.
- **2026-07-29** — `kits-virtuais-um-nivel-so` (EST-F015/EST-F022, Fatia 6): novo `ProductType`
  (`SIMPLES`/`KIT`) em `Product`; kit é virtual, explode em componentes na venda/estorno e nunca
  ganha linha própria em `stock_balance` (§2.10 do plano). Novo `product_kit_component`
  (`kit_sku`, `component_sku`, `quantity`), sem FK pelo mesmo motivo de `stock_balance`/
  `stock_movement` — `component_sku` pode ser SKU de variação. Ciclo e aninhamento são
  impossíveis por construção: `EstoqueService.defineKitRecipe`
  (`PUT /estoque/products/{sku}/kit`, nova permissão `ESTOQUE_KIT_MANAGE`) recusa componente que
  não seja `SIMPLES`, recusa promover a `KIT` um SKU já usado como componente de outro kit, e
  recusa kit com variações. `getStockBalance` deriva o saldo do kit como
  `min(floor(disponível_componente / quantidade_receita))`; `findPricingBySku` deriva o custo
  como a soma de `costPrice * quantity` dos componentes, preservando o `salePrice` próprio do kit
  — componente sem custo torna o custo do kit inteiro `null`, nunca zero. A explosão mora
  inteiramente em `EstoqueService.adjustStock` (chama a si mesmo por componente), então
  `PdvService.registerSale` e `OrderService.refundOrder` não precisaram de nenhuma mudança.
  `AJUSTE` direto num kit é rejeitado (`KitDirectAdjustmentException`); `reason` de cada
  movimento de componente ganha o sufixo `" (kit " + kitSku + ")"`. Migration V73. Bug corrigido
  de passagem, fora deste domínio: `CashbackService.findMarginImpact` excluía todo kit do
  relatório de impacto na margem por ler `product.pricing()` cru em vez de `findPricingBySku`.
  Coberto por `ProductTest`, novo `KitComponentTest`, casos novos em `EstoqueServiceTest`/
  `EstoqueControllerTest`/`EstoqueControllerSecurityTest`, e o novo `KitSaleFlowIT`.

## Próximos passos

A sprint de 2026-07-27 fechou C002, C003, C004, C005, C007, C008, C009, C010, C011, C012, F006 e F018.
Em 2026-07-29 fecharam também F013/F021/C013 (reserva), F014 (estorno/devolução, via
`OrderService.refundOrder`) e F015/F022 (kits, Fatia 6) — nenhum item do marco do marketplace
segue pendente neste módulo.

O roteiro completo para o que resta — ordem de execução, dependências entre os itens e os dois
que não cabem em estoque — está em [`proximos-passos.md`](proximos-passos.md). Resumo da
prioridade imediata (nenhuma bloqueia outro módulo):

1. **EST-F008** (lote e validade) e depois **EST-F007** (custo médio) — o custo entra por lote, e F007 destrava o DRE do `financeiro`.
2. **EST-F016** (unidade de medida) e **EST-F005** (entrada por XML de NF-e).
3. **EST-F012** (transferência entre depósitos) e **EST-F020** (preço por variação) seguem despriorizados por decisão — ver `proximos-passos.md`.

Fora do roteiro de código, EST-C011 deixou uma **pendência operacional**: rodar
`GET /estoque/integrity/orphan-skus` (ou o script) contra a base de produção e decidir o destino
de cada SKU levantado. É trabalho de conferência humana, não de implementação.
