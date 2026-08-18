# Domínio: vendas-balcao (PDV — Frente de Caixa)

**Status:** 🟢 Operacional — ciclo de caixa completo (abertura, sangria/suprimento, fechamento com conferência por forma de pagamento), venda com preço vindo do catálogo, pagamento com múltiplas formas, troco e comprovante interno (PDV-F006, Fatia 3), e comanda de mesa para consumo incremental de horas (PDV-F009).
**Pacote Java:** `com.cernecommerce...pdv` (packages não aceitam hífen; `pdv` ↔ `vendas-balcao`)
**Rota HTTP base:** `/pdv`
**Última atualização deste doc:** 2026-08-18 (PDV-F009, comanda de mesa — endpoints, schema e
testes novos; PDV-C001, auditoria de código — Regras de Negócio, Schema e Cobertura de Testes
preenchidos a partir do código, padrão [`estoque`](../estoque/README.md))

## Objetivo

Frente de caixa (PDV) das vendas locais da tabacaria: controle de fluxo de caixa e
registro de vendas no balcão.

## Escopo planejado

- **Fluxo de caixa:** abertura de caixa, sangria (retirada), suprimento e
  fechamento com conferência (valor esperado × contado). ✅ Implementado (PDV-F001/F002).
- **Itens de venda balcão:** registro de itens vendidos no balcão, vinculados à
  sessão de caixa aberta, **com baixa automática de estoque**. ✅ Implementado (EST-F010).

## Estrutura hexagonal

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/pdv/CashRegisterSession` (stub), `Sale`, `SaleItem` |
| ports/in | `core/ports/in/PdvUseCase` |
| ports/out | `core/ports/out/pdv/CashRegisterRepository`, `SaleRepository` |
| service | `core/service/PdvService` (wired em `CoreBeanConfig`, recebe `EstoqueUseCase`) |
| adapter/in | `adapter/in/controller/PdvController` → `GET /pdv/sessions?page&size` (`PDV_READ`), `POST /pdv/sessions/{id}/sales` (`PDV_SALE_MANAGE`) |

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/pdv/sessions` | `PDV_READ` | Lista sessões de caixa paginadas (`page` ≥ 0, `size` 1–100) |
| `POST` | `/pdv/sessions` | `PDV_SESSION_MANAGE` | Abre o caixa. Uma sessão aberta por operador; o depósito informado vale para todas as vendas dela |
| `GET` | `/pdv/sessions/current` | `PDV_READ` | Caixa aberto do operador autenticado |
| `GET` | `/pdv/sessions/{id}` | `PDV_READ` | Detalhe da sessão |
| `POST` | `/pdv/sessions/{id}/movements` | `PDV_SESSION_MANAGE` | Sangria ou suprimento. Exige sessão aberta **e do próprio operador** |
| `GET` | `/pdv/sessions/{id}/movements` | `PDV_READ` | Movimentos da sessão |
| `POST` | `/pdv/sessions/{id}/close` | `PDV_SESSION_CLOSE` | Fecha confrontando contado × esperado. **Divergência não bloqueia** |
| `GET` | `/pdv/sessions/{id}/payment-totals` | `PDV_READ` | Total recebido na sessão por forma de pagamento — só `CAPTURED` conta. As quatro formas sempre aparecem, mesmo zeradas |
| `GET` | `/pdv/pending-online-orders` | `PDV_READ` | Pedidos do app aguardando pagamento, para o caixa localizar quem chegou na loja |
| `POST` | `/pdv/sessions/{id}/orders/{orderId}/settle` | `PDV_SALE_MANAGE` | Liquida no balcão um pedido do app: consome a reserva e conclui |
| `POST` | `/pdv/sessions/{id}/sales` | `PDV_SALE_MANAGE` (+ `PDV_SALE_DISCOUNT` se houver desconto) | Registra venda na sessão, **captura o pagamento** e **dá baixa no estoque** item a item. Preço e custo vêm do catálogo, não do request. Exige sessão `OPEN` e ao menos uma linha em `payments` |
| `GET` | `/pdv/sales/{id}` | `PDV_READ` | Consulta um pedido, com os pagamentos. Antes de PDV-F005 a venda era write-only |
| `GET` | `/pdv/sales/{id}/receipt` | `PDV_READ` | Comprovante interno da venda — **não é documento fiscal** (isso é a NFC-e, Fatia 11) |
| `GET` | `/pdv/sessions/{id}/sales` | `PDV_READ` | Pedidos da sessão, paginados, do mais recente para o mais antigo |
| `POST` | `/pdv/comandas?sessionId=` | `PDV_COMANDA_MANAGE` | Abre comanda de mesa na sessão do operador (PDV-F009). Endpoint novo, controller próprio (`PdvComandaController`) |
| `POST` | `/pdv/comandas/{id}/items` | `PDV_COMANDA_MANAGE` | Lança item na comanda aberta — **debita estoque na hora**, não no fechamento |
| `GET` | `/pdv/comandas/{id}` | `PDV_READ` | Detalhe da comanda, com o total corrente (`runningTotal`) |
| `GET` | `/pdv/comandas?sessionId=` | `PDV_READ` | Comandas abertas de uma sessão — as "mesas ocupadas" |
| `POST` | `/pdv/comandas/{id}/close` | `PDV_COMANDA_MANAGE` | Fecha a comanda: converte os itens acumulados num pedido concluído. Mesmo contrato de pagamento de `POST /pdv/sessions/{id}/sales`; **sem novo débito de estoque** — já saiu item a item |
| `POST` | `/pdv/comandas/{id}/cancel` | `PDV_COMANDA_MANAGE` | Abandona a comanda sem cobrança, devolvendo ao estoque cada item já lançado (`ENTRADA`) |

> **Contrato alterado em PDV-F004/F006** (sem consumidor real — o PDV do `frontend-admin` é
> protótipo mockado): `unitPrice` saiu do corpo de `POST /pdv/sessions/{id}/sales`,
> `discountAmount`, `customerId` e `payments` entraram (`payments` é **obrigatório**, pelo menos
> uma linha). Produto sem preço recusa a venda com `409 PRODUCT_NOT_PRICED`; desconto acima do
> teto (`pdv.sale.max-discount-percent`, default 10%) responde `409 DISCOUNT_LIMIT_EXCEEDED`;
> pagamento insuficiente responde `400 INSUFFICIENT_PAYMENT`; débito/crédito/PIX que sozinhos
> passam do total do pedido respondem `409 PAYMENT_EXCEEDS_ORDER_TOTAL` — só dinheiro pode ser
> tendido a mais para gerar troco. Detalhes em
> [`docs/api-reference.md`](../../api-reference.md#pdv-vendas-balcão--pdv).

## Regras de Negócio Implementadas

| Regra | Onde | Teste |
|---|---|---|
| **Ciclo de caixa** | | |
| Um caixa aberto por operador — checagem amigável antes do índice parcial do banco | `PdvService.openSession` (`findOpenByOperator`) | `PdvServiceTest.openSession_refusesASecondOpenSessionForTheSameOperator` |
| Depósito é validado na abertura, antes de carimbar na sessão | `PdvService.openSession` | `PdvServiceTest.openSession_validatesTheWarehouseBeforeStampingItOnTheSession` |
| Sangria/suprimento exige motivo não-branco — motivo é registrado no momento da retirada, não reconstituído depois | `CashMovement.register` (compact constructor) | `CashMovementTest.rejectsBlankReason` |
| Sinal do movimento vem do tipo, nunca do valor (`amount` sempre positivo) | `CashMovement.signedAmount()` | `CashMovementTest.signedAmount_derivesTheSignFromTheTypeNotTheValue` |
| Registrar movimento exige sessão aberta e do próprio operador | `PdvService.registerCashMovement` (`requireOwnOpenSession`) | `PdvServiceTest.registerCashMovement_requiresTheSessionToBelongToTheOperator`, `registerCashMovement_refusesOnClosedSession` |
| Fechar **não** exige ser dono da sessão — é o gerente quem confere | `PdvService.closeSession` (sem `requireOwnOpenSession`) | `PdvServiceTest.closeSession_doesNotRequireBeingTheOwner` |
| Fechamento só soma `DINHEIRO` capturado no `expectedAmount`; débito/crédito/PIX se conferem contra a adquirente, não contra a gaveta | `PdvService.closeSession` | `PdvServiceTest.closeSession_ignoresNonCashPaymentsInTheExpectedAmount`, `PdvCashCycleIT.splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer` |
| `expectedAmount` = abertura + `DINHEIRO` capturado − sangrias + suprimentos | `PdvService.closeSession` | `PdvServiceTest.closeSession_computesExpectedFromOpeningCashSalesAndMovements` |
| Divergência (contado × esperado) não bloqueia o fechamento | `CashRegisterSession.closedWith` | `CashRegisterSessionTest.closedWith_doesNotBlockOnDivergence` |
| Fechar sessão já fechada é rejeitado | `CashRegisterSession.closedWith` | `CashRegisterSessionTest.closedWith_refusesToCloseTwice`, `PdvServiceTest.closeSession_refusesAnAlreadyClosedSession` |
| Venda cancelada não conta no `expectedAmount` do fechamento | `PdvService.closeSession` (soma só `CAPTURED`) | `PdvCashCycleIT.cancelledSaleDoesNotCountTowardsTheExpectedAmount` |
| `GET .../payment-totals` sempre lista as 4 formas de balcão, mesmo zeradas (exceto `GATEWAY_PIX`, nunca ligado a sessão) | `PdvService.getSessionPaymentTotals` | `PdvServiceTest.getSessionPaymentTotals_returnsAllFourMethodsEvenWhenUnused` |
| **Venda de balcão (`registerSale`)** | | |
| Venda exige sessão aberta e do próprio operador | `PdvService.registerSale` (`requireOwnOpenSession`) | `PdvServiceTest.registerSale_refusesASessionThatBelongsToAnotherOperator`, `PdvControllerSecurityTest` (403 `SESSION_NOT_OWNED`) |
| Preço e custo vêm do catálogo, nunca do request — servidor sempre reprecifica | `OrderItem.fromCatalog` via `EstoqueUseCase.resolveSaleInfo` | `PdvServiceTest.registerSale_resolvesPriceAndCostFromTheCatalog`, `PdvControllerSecurityTest.register_sale_with_client_supplied_price_is_ignored_and_server_price_is_used` |
| SKU sem preço recusa a venda com 409 `PRODUCT_NOT_PRICED`, antes de tocar o estoque | `OrderItem.fromCatalog` (lança `ProductNotPricedException`) | `OrderItemTest.fromCatalog_rejectsProductWithoutPrice`, `PdvServiceTest.registerSale_refusesProductWithoutPriceBeforeTouchingStock` |
| Depósito da venda vem da sessão, nunca do request (PDV-C004) | `PdvService.registerSale` (`session.warehouseCode()`) | `PdvServiceTest.registerSale_takesTheWarehouseFromTheSessionNotFromTheCaller` |
| Desconto acima do teto configurado é 409 `DISCOUNT_LIMIT_EXCEEDED` | `PdvService.requireDiscountWithinLimit` | `PdvServiceTest.registerSale_appliesDiscountWithinTheLimit`, `registerSale_refusesDiscountAboveTheLimitBeforeTouchingStock` |
| Conceder desconto exige `PDV_SALE_DISCOUNT` — checagem **programática** no controller, não `@PreAuthorize` (decide antes de olhar o payload, e desconto depende do corpo) | `PdvController.requireDiscountAuthority` | `PdvControllerSecurityTest` |
| Pagamento é validado **antes** de tocar o estoque | `PdvService.validatePaymentsAndComputeChange`, chamado antes do loop de `adjustStock` | `PdvServiceTest.registerSale_throwsInsufficientPaymentBeforeTouchingStock` |
| Só `DINHEIRO` pode exceder o líquido do pedido (gera troco); débito/crédito/PIX sozinhos não podem passar do total | `PdvService.validatePaymentsAndComputeChange` | `PdvServiceTest.registerSale_computesChangeFromCashOverpayment`, `registerSale_throwsWhenNonCashPaymentAloneExceedsTheOrderTotal` |
| Troco em pagamento dividido considera só a parte em dinheiro, não a soma de tudo | `PdvService.validatePaymentsAndComputeChange` | `PdvServiceTest.registerSale_computesChangeFromCashPortionOnlyInASplitPayment`, `PdvCashCycleIT.splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer` |
| Pagamento insuficiente é 400 `INSUFFICIENT_PAYMENT` | `PdvService.validatePaymentsAndComputeChange` | `PdvServiceTest.registerSale_throwsInsufficientPaymentBeforeTouchingStock` |
| SKU desconhecido ou saldo insuficiente reverte a venda inteira — nada é salvo | Propagação de `ProductNotFoundException`/`InsufficientStockException` até o chamador | `PdvServiceTest.registerSale_propagatesUnknownSkuAndDoesNotSaveOrder`, `registerSale_propagatesInsufficientStockAndDoesNotSaveOrder` |
| Estoque é debitado item a item (`SAIDA`), com o número da sessão no motivo do ledger | `PdvService.registerSale` → `EstoqueUseCase.adjustStock` por item | `PdvServiceTest.registerSale_adjustsStockPerItemWithTheSessionInTheReason` |
| `reserveForPickup=true` grava `RESERVADO` em vez de `CONCLUIDO` — mercadoria já baixada e pagamento já capturado, retirada fica para depois (PDV-F008) | `Order.reserved`, `PdvService.registerSale` | `PdvServiceTest.registerSale_reserveForPickupTrue_savesReservadoInsteadOfConcluido`, `registerSale_reserveForPickupTrue_aindaBaixaEstoqueECapturaPagamento` |
| Numeração fiscal (`orderNumber`) só é emitida na conclusão/reserva — nunca na criação em memória, para não deixar buraco na sequência em venda revertida | `Order.concluded`/`Order.reserved` chamam `nextOrderNumber()` | `PdvCashCycleIT.orderNumbersAreUniqueAcrossSales`, `PedidoRepositoryIT.nextOrderNumber_neverRepeats` |
| Venda anônima (sem cliente identificado) é permitida | `PdvService.registerSale` | `PdvServiceTest.registerSale_allowsAnonymousSale` |
| Cashback é registrado só depois de o pedido concluído estar salvo | `PdvService.registerSale` → `CashbackUseCase.recordEarnedForOrder` | `PdvServiceTest.registerSale_recordsEarnedCashbackAfterSavingTheConcludedOrder` |
| **Liquidação de pedido online (`settleOnlineOrder`)** | | |
| Liquidar no balcão **consome a reserva** de checkout, nunca debita estoque de novo | `PdvService.settleOnlineOrder` → `EstoqueUseCase.consumeReservationsByOwner` | `PdvServiceTest.settleOnlineOrder_consumesTheReservationInsteadOfDebitingStockAgain` |
| Canal permanece `MARKETPLACE` — só o `sessionId` muda ao ser liquidado no caixa que recebeu o dinheiro | `Order.withSession` | `PdvServiceTest.settleOnlineOrder_keepsTheChannelAndAttachesTheCashSession` |
| Só liquida pedido `AGUARDANDO_PAGAMENTO`; e exige posse da sessão | `PdvService.settleOnlineOrder` | `PdvServiceTest.settleOnlineOrder_refusesAnOrderThatIsNotAwaitingPayment`, `settleOnlineOrder_refusesASessionThatBelongsToAnotherOperator` |
| **Máquina de estados do pedido (`OrderStatus`/`Order`)** | | |
| Canal determina campo obrigatório: `MARKETPLACE` exige `customerId`, `BALCAO` exige `sessionId` | `Order` (compact constructor) | `OrderTest.marketplaceRequiresCustomer`, `balcaoRequiresSession` |
| `changeAmount` só é permitido em `BALCAO` | `Order` (compact constructor) | `OrderTest.changeAmountOnlyExistsInBalcao` |
| `netAmount = grossAmount − discountAmount − cashbackRedeemed`, sempre recalculado, nunca aceito do cliente | `Order` (compact constructor) | `OrderTest.rejectsNetAmountThatDoesNotMatchTheOtherTotals` |
| Transições seguem estritamente a tabela de `OrderStatus`; fora dela é `InvalidOrderStatusTransitionException` | `OrderStatus.canTransitionTo` | `OrderStatusTest.everyStatusDeclaresItsTransitions`, `nullTargetIsNeverAllowed` |
| Pré-pagamento (`CRIADO`/`AGUARDANDO_PAGAMENTO`) só alcança `CANCELADO`; pós-pagamento só alcança `REEMBOLSADO` — máquina estritamente partida | `OrderStatus` (tabela de transições) | `OrderStatusTest.prePaymentStatesCanBeCancelledButNotRefunded`, `postPaymentStatesCanBeRefundedButNotCancelled` |
| `RESERVADO` só é alcançável a partir de `CRIADO` — por construção, pedido de marketplace nunca chega lá (nunca passa por `CRIADO`), sem checagem de canal em nenhum lugar | `OrderStatus`, `Order.reserved` | `OrderTest.reserved_isUnreachableAfterTheOrderIsAlreadyConcluded`, `OrderStatusTest.reservadoIsUnreachableFromTheMarketplacePath` |
| `RESERVADO → CONCLUIDO` (retirada) carimba `concludedAt`, diferente do `withStatus` genérico (que não carimba nada) | `Order.pickedUp` | `OrderTest.pickedUp_stampsConcludedAtAndKeepsReservedAtAsHistory`, `OrderServiceTest.changeStatus_reservadoParaConcluido_usaPickedUpEStampaConcludedAt` |
| `CANCELADO`⇔`cancelledAt` e `REEMBOLSADO`⇔`refundedAt` são consistência obrigatória | `Order` (compact constructor) | `OrderTest.cancelledStatusAndTimestampMustAgree`, `refundedStatusAndTimestampMustAgree` |
| **Item do pedido (`OrderItem`)** | | |
| `unitPrice`/`costPrice`/`cashbackPercent` são snapshots congelados no instante da venda — mudança futura no catálogo não reescreve pedido passado | `OrderItem.fromCatalog` | `OrderItemTest.fromCatalog_freezesPriceAndCostFromPricing` |
| `discountAmount ≤ quantity × unitPrice` — desconto que zera o item é devolução, não venda | `OrderItem` (compact constructor) | `OrderItemTest.rejectsDiscountGreaterThanGross`, `acceptsDiscountEqualToGross` |
| `cashbackPercent` em `[0,100]` | `OrderItem` (compact constructor) | `OrderItemTest.rejectsCashbackPercentOutOfRange` |
| Venda abaixo do custo é sinalizada (`marginAmount` negativo), nunca bloqueada — queima de estoque é decisão comercial legítima | `OrderItem.marginAmount()` | `OrderItemTest.marginAmount_isNegativeWhenSellingBelowCost` |
| **Pagamento (`OrderPayment`)** | | |
| Venda de balcão nasce `CAPTURED` diretamente — dinheiro já na gaveta no instante da venda, sem fluxo de autorização assíncrona | `OrderPayment.captured` | `OrderPaymentTest.captured_startsAsCapturedWithTimestampsSet` |
| `amount` é sempre positivo, mesmo em `DINHEIRO` — troco é `Order.changeAmount`, nunca linha de pagamento negativa | `OrderPayment` (compact constructor) | `OrderPaymentTest.rejectsNonPositiveAmount` |
| Estorno é sempre uma linha **nova** (ledger append-only), nunca update — exceto `confirmCaptured`, a única exceção, por causa do `UNIQUE` em `gateway_ref` | `OrderPayment.refunded`/`confirmCaptured` | `OrderPaymentTest.refunded_startsAsRefundedWithSameMethodAndAmount`, `confirmCaptured_transitionsPendingToCapturedPreservingIdentity` |
| `installments` só é válido com `CREDITO`, faixa `[1,24]` | `OrderPayment` (compact constructor) | `OrderPaymentTest.installments_onlyAllowedWithCredito`, `installments_mustBeWithinRange` |
| **Cancelamento e reembolso (`OrderService`)** | | |
| Cancelar (pré-pagamento) libera a reserva de estoque, **nunca** ajusta saldo real — venda pré-paga nunca teve baixa de verdade, só reserva | `OrderService.cancelOrder` → `EstoqueUseCase.releaseReservationsByOwner` | `OrderServiceTest.cancelOrder_releasesTheReservationInsteadOfTouchingRealStock` |
| Reembolsar (pós-pagamento) devolve estoque via `ENTRADA` por item, estorna cada pagamento `CAPTURED`, reverte cashback `EARNED` ainda não revertido — tudo na mesma transação | `OrderService.refundOrder` | `OrderServiceTest.refundOrder_returnsTheGoodsToStock`, `refundOrder_reversesEachCapturedPaymentWithMatchingMethodAndAmount`, `refundOrder_invokesCashbackReversalForTheOrder` |
| Reembolso funciona também em pedido já entregue — é devolução, não desfazer venda | `OrderService.refundOrder` | `OrderServiceTest.refundOrder_worksOnADeliveredOrderBecauseThatIsAReturn` |
| Duplo cancelamento/reembolso é rejeitado pela própria máquina de estados (ambos terminais) | `OrderStatusTest`, propagação de `InvalidOrderStatusTransitionException` | `OrderServiceTest.cancelOrder_refusesToCancelTwiceAndDoesNotReleaseAgain`, `refundOrder_refusesToRefundTwiceAndDoesNotTouchStock` |
| Reembolso concorrente: só um sucede | `OrderRefundConcurrencyIT` | `OrderRefundConcurrencyIT.concurrentRefunds_onlyOneSucceedsAndEffectsAreNotDuplicated` |
| **Concorrência** | | |
| Vendas concorrentes nunca vendem além do saldo disponível | `EstoqueUseCase.adjustStock` (`@Version` otimista, ver [`estoque`](../estoque/README.md)) | `PdvSaleConcurrencyIT.concurrentSales_neverOversellBeyondAvailableStock` |

## Segurança e Infraestrutura

> Mecanismos transversais em [`docs/security.md`](../../security.md); ambientes e containers em
> [`docs/infrastructure.md`](../../infrastructure.md); o modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura). Aqui fica só o recorte
> deste domínio.

### Permissões RBAC

| Permissão | Libera | Migration | Semeada em `dev`? |
|---|---|---|---|
| `PDV_READ` | `GET /pdv/sessions` | V53 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `PDV_SALE_MANAGE` | `POST /pdv/sessions/{id}/sales` | V57 | ✅ desde **EST-C001** (antes faltava, e o endpoint respondia 403 em `dev`) |
| `PDV_SALE_DISCOUNT` | desconto > 0 em `POST /pdv/sessions/{id}/sales` | V65 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `PDV_SESSION_MANAGE` | abertura de caixa e movimentos | V66 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `PDV_SESSION_CLOSE` | fechamento com conferência | V66 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |
| `PDV_COMANDA_MANAGE` | `POST`/`.../items`/`.../close`/`.../cancel` de `/pdv/comandas` | V105 | ✅ `SeedConfig` + `DevRoleBootstrapConfig` |

Comanda (PDV-F009) ganhou permissão **própria**, separada de `PDV_SALE_MANAGE` — é uma superfície
operacional diferente (tab de horas vs. venda pontual), e granularidade de concessão separada não
custa mais que esta linha a mais de `@PreAuthorize`. Leitura continua sob `PDV_READ`.

Pagamento (PDV-F006, V68) **não trouxe permissão nova**: capturar pagamento é parte do próprio
`registerSale`, sob `PDV_SALE_MANAGE`; ler pagamento/totais/comprovante é `PDV_READ`, como o resto
da leitura do módulo.

`PDV_SESSION_CLOSE` é separada de `PDV_SESSION_MANAGE` porque a conferência do fechamento costuma
ser do gerente, não de quem operou o caixa — e é a única operação da sessão que **não** exige ser o
dono dela.

> A checagem de `PDV_SALE_DISCOUNT` é **programática**, no controller, e não por `@PreAuthorize`:
> ela depende do corpo da requisição, e o `@PreAuthorize` decide antes de olhar o payload.

⚠️ **`PDV_SALE_MANAGE` movimenta estoque sem exigir nenhuma permissão `ESTOQUE_*`.**
`PdvService.registerSale` chama `EstoqueUseCase.adjustStock` diretamente; o `@PreAuthorize` só
existe na borda HTTP. Quem registra venda dá baixa em qualquer SKU de qualquer depósito. É a
contrapartida esperada de um PDV, mas convém saber ao conceder a permissão.

O PDV é o módulo que mais expõe a lacuna do ciclo de caixa: **não há endpoint de abertura de
sessão** (PDV-F001), então hoje o operador registra venda em sessões criadas manualmente no
banco — sem controle de quem abriu, quando, nem com qual fundo de troco.

### Rate limiting

❌ Nenhum endpoint deste módulo é limitado. Ver PLAT-C030.

### Isolamento de dados

Single-tenant. O vínculo operador↔caixa **foi fechado em PDV-C004**: registrar venda e movimentar
dinheiro exigem que a sessão pertença ao operador autenticado (`403 SESSION_NOT_OWNED`), e o
depósito da venda vem da sessão em vez do request — o operador não baixa estoque de depósito alheio.

A única operação da sessão que não exige posse é o **fechamento**, deliberadamente: a conferência é
do gerente.

> ⚠️ **A garantia de "uma sessão aberta por operador" não é exercitada por teste.** Ela existe em
> dois lugares — checagem no domínio e índice parcial único `uk_cash_register_session_open_operator`
> (V66) —, mas o perfil `dev` monta o schema por `ddl-auto` e o H2 não suporta índice parcial. Sob
> concorrência, só o Postgres protege, e isso nunca foi testado. Rastreado como **PLAT-C035**.

### Auditoria

✅ `POST /pdv/sessions/{id}/sales` publica `AuditEvent` do tipo `STOCK_MOVEMENT_REGISTERED`, com
`origin: PDV_SALE`, `sessionId`, `warehouseCode`, `type` e a lista de `skus` vendidos — um evento
por venda, não por item. O rastro item a item continua no `stock_movement`, com
`reason = "Venda balcão sessão #{id}"` e o `username` de quem chamou. Resolvido em PDV-C003 /
`EST-C004` (2026-07-27).

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `cash_register_session`, `cash_register_sale`, `sale_item` (V57) | módulo indisponível |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior |
| `EstoqueUseCase` (chamada síncrona in-process) | baixa de saldo por item + alerta de reposição | venda inteira falha e reverte |

Sem fila, sem impressora fiscal, sem integração de pagamento. Venda e baixa de estoque
compartilham a **mesma transação**: `InsufficientStockException` em qualquer item reverte a
venda inteira, e nada é persistido.

### Limites operacionais

- `GET /pdv/sessions`: `page` ≥ 0 e `size` entre 1 e 100, via Bean Validation (`@Validated` no
  controller).
- `POST /pdv/sessions/{id}/sales`: itens obrigatórios e quantidade > 0 via `@Valid`; **sem teto
  de itens por venda**. O total é calculado no servidor (`SaleItem.subtotal()`), nunca aceito
  do cliente.

### Riscos conhecidos

- **PLAT-C035** — a garantia de "uma sessão aberta por operador" depende de um índice parcial que
  o H2 não suporta; sob concorrência, só o Postgres protege, e isso nunca foi testado.
- **PLAT-C030** — sem rate limit.
- **PDV-F009** — a baixa de estoque da comanda não é transacionalmente atômica ao longo da vida
  dela: cada `POST /pdv/comandas/{id}/items` debita e commita por conta própria (não dá para
  segurar uma transação de banco aberta pelas horas em que uma comanda fica em uso). Uma comanda
  esquecida aberta, sem `POST .../cancel` explícito, deixa estoque debitado sem devolução
  automática — não há varredura/timeout para esse caso.

## Integração com estoque

`PdvService.registerSale` (`core/service/PdvService.java:47`) chama
`EstoqueUseCase.adjustStock(..., MovementType.SAIDA, ...)` para cada item, com o motivo
`Venda balcão sessão #{sessionId}`, e só então persiste a `Sale` — tudo na mesma transação.
Saldo insuficiente em qualquer item reverte a venda inteira. A baixa também dispara o alerta
de ponto de reposição. Detalhes em [`estoque`](../estoque/README.md#integrações-entre-domínios).

## Schema de Banco (Migrations)

**V57 — `pdv_cash_register_and_sale`**
- `cash_register_session` (id, operator, opened_at, opening_amount, closed_at, status) — versão
  original, sem depósito nem conferência (chegaram na V66).
- `cash_register_sale` (id, session_id FK → `cash_register_session` ON DELETE CASCADE,
  warehouse_code, sold_at, total_amount) — índice `idx_cash_register_sale_session_id`.
- `sale_item` (id, sale_id FK ON DELETE CASCADE, sku, quantity, unit_price) — índice
  `idx_sale_item_sale_id`.
- Seed `PDV_SALE_MANAGE` para `ROLE_ADMIN`.

**V65 — `pedido_sales_order`** (PDV-F003/F004/F005 — fundação do pedido, o grande rename)
- `cash_register_sale → sales_order`, `sale_item → order_item` (`sale_id→order_id`), índices
  renomeados junto. **Decisão central**: venda de balcão e pedido de marketplace viram a mesma
  entidade, discriminada por `channel` — evita todo consumidor futuro (extrato, cashback,
  devolução, faturamento) ter que fazer `UNION` entre duas tabelas.
- `sales_order` ganha: `channel`, `status`, `order_number`, `customer_id` FK → `customers`,
  `discount_amount DEFAULT 0`, `cashback_redeemed DEFAULT 0`, `net_amount`, `change_amount`,
  `cancel_reason`, `paid_at`, `concluded_at`, `cancelled_at`, `version BIGINT DEFAULT 0`;
  `session_id` vira nullable (marketplace não tem caixa).
- Backfill dos pedidos legados: `channel='BALCAO'`, `status='CONCLUIDO'`, `order_number` prefixado
  `LEG-` (nunca colide com a sequência nova).
- `CHECK`s: `ck_sales_order_channel` (`BALCAO`/`MARKETPLACE`), `ck_sales_order_status` (lista
  fechada, estendida em V71/V98), `ck_sales_order_customer_by_channel` (marketplace exige
  cliente), `ck_sales_order_session_by_channel` (balcão exige sessão), `ck_sales_order_amounts_non_negative`,
  `ck_sales_order_net_amount` (`net = gross − discount − cashback`),
  `ck_sales_order_cancelled_consistency`, `ck_sales_order_change_only_balcao` (troco só existe
  onde há dinheiro em espécie). `uk_sales_order_number UNIQUE`.
- `CREATE SEQUENCE order_number_seq START 1000` — sequência **própria**, não o `id` da tabela:
  `BIGSERIAL` deixa buraco em rollback, e buraco em numeração fiscal é problema com o fisco.
- `order_item` ganha `cost_price`, `discount_amount DEFAULT 0`, `cashback_percent` — todos
  **nulos nos itens legados de propósito** (um `DEFAULT 0` mentiria sobre margem/cashback
  histórico). `CHECK`s: `ck_order_item_quantity_positive`, `ck_order_item_discount_within_gross`,
  `ck_order_item_cashback_percent_range`.
- Índices `idx_sales_order_customer_id`, `idx_sales_order_channel_status`,
  `idx_sales_order_concluded_at DESC`.
- Seed `PDV_SALE_DISCOUNT` (separada de vender — conceder abatimento é decisão comercial).

**V66 — `pdv_cash_cycle`** (PDV-F001/F002/C004 — ciclo de caixa)
- `cash_register_session` ganha `warehouse_code`, `closed_by`, `expected_amount`,
  `counted_amount`, `difference_amount`. Backfill de `warehouse_code` a partir do primeiro
  depósito `LOJA_FISICA` (único palpite honesto numa tabacaria de uma loja só).
- **Índice único parcial** `uk_cash_register_session_open_operator ON (operator) WHERE
  status='OPEN'` — o domínio já checa antes de abrir, mas é este índice que sobrevive a duas
  requisições simultâneas do mesmo operador.
- `CHECK`s: `ck_cash_register_session_closed_consistency` (`CLOSED` ⇔ `closed_at`+`counted_amount`),
  `ck_cash_register_session_amounts`.
- Nova tabela `cash_movement` (id, session_id FK ON DELETE CASCADE, type, amount, reason,
  username, created_at) — ledger, não contador mutável, pela mesma razão de `stock_movement`:
  o esperado é *derivado*, não armazenado. `CHECK`s `ck_cash_movement_amount_positive` (`>0`,
  sinal vem do `type`) e `ck_cash_movement_type` (`SANGRIA`/`SUPRIMENTO`). Índice
  `idx_cash_movement_session_id`.
- Seed `PDV_SESSION_MANAGE` e `PDV_SESSION_CLOSE` — abrir/sangrar é operação de turno; fechar é
  conferência, e quem confere não precisa ser quem operou.

**V68 — `pdv_order_payment`** (PDV-F006 — pagamento com múltiplas formas e troco)
- Nova tabela `order_payment` (id, order_id FK → `sales_order` ON DELETE CASCADE, method,
  amount `CHECK > 0`, status, installments, gateway_ref, authorized_at, captured_at,
  created_at). Balcão grava direto em `CAPTURED` — dinheiro já na gaveta.
- `CHECK`s: `ck_order_payment_method` (lista fechada, estendida em V79), `ck_order_payment_status`,
  `ck_order_payment_captured` (`CAPTURED` ⇔ `captured_at`), `ck_order_payment_installments`
  (só `CREDITO`, 1–24).
- Índice `idx_order_payment_order_id`; **índice único parcial**
  `uk_order_payment_gateway_ref ON (gateway_ref) WHERE gateway_ref IS NOT NULL` — idempotência de
  webhook criada **muito antes** do gateway (Fatia 10) existir, por decisão de risco do plano.

**V71 — `pedido_reembolso`** (PDV-F007, Fatia 5)
- `ck_sales_order_status` recriado para incluir `REEMBOLSADO`. Nova coluna `refunded_at` +
  `ck_sales_order_refunded_consistency`. `CANCELADO` fica reservado a pedido cancelado **antes**
  de pagamento confirmado; `REEMBOLSADO` é a única saída pós-pagamento — nunca os dois.

**V72 — `order_refund_permission`**
- Seed `ORDER_REFUND`, deliberadamente separada de `ORDER_CANCEL` — reembolso estorna dinheiro de
  verdade, cancelamento pré-pagamento não mexe em nada.

**V79 — `marketplace_payment_gateway`** (ECM-F004, Fatia 10)
- Só amplia `ck_order_payment_method` para incluir `GATEWAY_PIX` — o resto do schema (gateway_ref
  nulável com índice único parcial, status já com `PENDING`/`AUTHORIZED`/…) já estava pronto
  desde a V68.

**V98 — `pedido_reservado`** (PDV-F008)
- `ck_sales_order_status` recriado para incluir `RESERVADO`. Nova coluna `reserved_at`, **sem**
  `CHECK` de coexistência com o status atual — é histórico puro, mesma régua de `paid_at`,
  permanece preenchida depois de `RESERVADO → CONCLUIDO`.

**V99 — `order_item_product_name`**
- `order_item.product_name` — nome do produto congelado no instante da venda, mesma razão de
  `cost_price`: renomear o produto depois não pode reescrever o histórico.

**V100 — `pedido_esteira_timestamps`**
- `sales_order` ganha `separated_at`, `shipped_at`, `delivered_at` — sem `CHECK` de coexistência,
  mesma régua de `reserved_at`/`paid_at`.

**Nota de modelagem:** `sales_order`/`order_item`/`order_payment` referenciam depósito só por
texto livre (`warehouse_code`), sem FK — mesmo padrão de `stock_balance`/`stock_movement` em
`estoque` (ver EST-C002 no README daquele domínio). Nenhuma validação equivalente a
`ProductRepository.existsBySku` existe para `warehouse_code` neste módulo hoje.

## Cobertura de Testes

| Arquivo | Tipo | O que cobre |
|---|---|---|
| `CashRegisterSessionTest` | Unit (domínio) | `open`, `closedWith` (transição, divergência, double-close), invariantes de `of()`, `belongsTo` |
| `CashMovementTest` | Unit (domínio) | `register`, `signedAmount` (sinal vem do tipo), invariantes, reconstituição |
| `OrderTest` | Unit (domínio) | `openBalcao`/`openMarketplace`, todas as transições (incl. `reserved`/`pickedUp`/reembolso pós-reserva), violações de invariante, cópia defensiva de itens |
| `OrderItemTest` | Unit (domínio) | `fromCatalog` (resolução de preço/custo), derivação de margem/cashback, violações de invariante |
| `OrderStatusTest` | Unit (domínio) | Completude da tabela de transições, estados terminais, validação de caminho por canal |
| `OrderPaymentTest` | Unit (domínio) | `captured`/`pending`/`refunded`/`confirmCaptured`, invariantes |
| `PdvServiceTest` | Unit (Mockito) | Sessão, fechamento, movimentos, venda (posse, preço do catálogo, desconto, ordem de validação de pagamento antes do estoque, `reserveForPickup`, cálculo de troco incl. pagamento dividido, propagação de falhas sem salvar), liquidação online |
| `PdvCashCycleIT` | `@SpringBootTest` | Ciclo completo (abrir → vender → sangrar → fechar), pagamento dividido, venda cancelada fora do esperado, netting de movimentos, isolamento entre operadores, unicidade de numeração |
| `PdvSaleConcurrencyIT` | `@SpringBootTest` | Vendas concorrentes nunca vendem além do saldo |
| `OrderServiceTest` | Unit (Mockito) | `changeStatus` (incl. caso especial `pickedUp`), `cancelOrder` (libera reserva), `refundOrder` (fan-out de estoque/pagamento/cashback), guardas de duplo cancelamento/reembolso |
| `OrderRefundIT` | `@SpringBootTest` | Reembolso e cancelamento fim a fim |
| `OrderRefundConcurrencyIT` | `@SpringBootTest` | Reembolso concorrente: só um sucede |
| `PdvControllerTest` | MockMvc standalone | Contrato HTTP básico dos endpoints principais |
| `PdvControllerSecurityTest` | MockMvc + Security | 401/403 por autoridade, 404s, 400s, posse de sessão, preço servidor-side (ignora preço enviado pelo cliente) |
| `PedidoRepositoryIT` | `@SpringBootTest` + `@Transactional` | Numeração de pedido, round-trip de todo campo congelado, combinações de filtro, sobrevivência de `reservedAt`/timestamps da esteira |
| `PedidoRepositoryPostgresIT` | `@SpringBootTest` (Postgres real, via Testcontainers) | Variante da IT acima nas particularidades do dialeto Postgres |

**Lacunas conhecidas** (registradas para não maquiar como "tudo coberto"):
1. Não existe IT de persistência **dedicado** para `CashRegisterSession`/`CashMovement` —
   a cobertura de round-trip vem só indiretamente de `PdvCashCycleIT`.
2. **PLAT-C035** — a garantia "uma sessão aberta por operador" depende do índice parcial único
   `uk_cash_register_session_open_operator`, que o H2 (perfil `dev`, `ddl-auto=create-drop`) não
   suporta. Só o Postgres real protege essa invariante sob concorrência, e esse caminho nunca foi
   exercitado por teste.

## Testes no Postman

Coleção do módulo: [`vendas-balcao.postman_collection.json`](vendas-balcao.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/vendas-balcao/vendas-balcao.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Pré-requisitos (domínio estoque)` | cria depósito, produto e dá entrada de 50 unidades |
| `02 — Sessões de caixa` | listagem paginada (guarda a primeira sessão `OPEN`) e o 400 de `page` negativa |
| `03 — Venda no balcão` | venda de 2 itens com o total calculado no servidor e a conferência da baixa no estoque |
| `04 — Casos de erro` | saldo insuficiente (com prova de que o saldo **não** mudou — rollback da venda inteira), sessão inexistente, venda sem itens e 401 |

> **Nota histórica:** a coleção descreve o fluxo de quando a abertura de caixa ainda não tinha
> endpoint (abrir sessão direto pelo banco) — hoje `POST /pdv/sessions` já existe (PDV-F001, ver
> Histórico). A ressalva de `PDV_SALE_MANAGE` não semeada em `dev` também já foi corrigida
> (EST-C001, backlog de estoque).

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| PDV-F007 | 🟢 Baixa | Feature | marcar-pedido-como-reembolsado | Status `REEMBOLSADO`, distinto de `CANCELADO`, com estorno do pagamento e `REVERSED` no ledger de cashback. Cancelar e reembolsar são eventos diferentes: contá-los juntos esconde quanto dinheiro de fato voltou ao cliente. `order_payment` já existe (Fatia 3, 2026-07-29); falta a Fatia 4 (cashback) para ter o que reverter dos dois lados. Acréscimo de enum + `CHECK`, barato agora que as duas existirem. Levantado com o dono em 2026-07-28. | ✅ Fechado (Fatia 5, 2026-07-29) — exatamente como especificado: `REEMBOLSADO` separado de `CANCELADO`, `cancelOrder` (pré-pagamento) e `refundOrder` (pós-pagamento, estorna pagamento e cashback) como ações distintas. V71/V72. |
| PDV-F008 | 🟡 Importante | Feature | reserva-para-retirada | Status `RESERVADO`: venda de balcão paga e baixada do estoque, aguardando o cliente retirar depois — hoje resolvido informalmente (papel/caderno). Pedido do `mahal-admin` `BACKEND_TODO.md` §"PDV: status RESERVADO", frontend já pronto atrás de `RESERVAS_ENABLED`. | ✅ Fechado (2026-08-17) — ver Histórico abaixo. |
| PDV-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Preencher Regras de Negócio, Schema (V57) e Cobertura de Testes no padrão de `estoque`. | ✅ Fechado (2026-08-18) — três seções preenchidas a partir do código; duas lacunas reais documentadas em vez de maquiadas (ver "Lacunas conhecidas" em Cobertura de Testes). |
| PDV-F009 | 🟡 Média | Feature | comanda-de-mesa-para-lounge | O PDV atual modela venda pontual de balcão; um lounge de narguilé vive de comandas abertas por horas, com pedidos incrementais (essência, carvão, bebida) e fechamento único — hoje isso é resolvido informalmente. Proposta: entidade `Comanda` (mesa/cliente, `ABERTA`→`FECHADA`) agregando múltiplos `OrderItem` incrementais na mesma sessão de caixa, reaproveitando `EstoqueUseCase.adjustStock` item a item como já faz `registerSale`, com fechamento único somando tudo e dividindo entre pagamentos (múltiplas formas já suportado por `PDV-F006`). Sugerido em análise de inovação de 2026-08-18. | ✅ Fechado (2026-08-18) — ver Histórico abaixo. |

> A permissão `PDV_SALE_MANAGE` está ausente dos seeders de dev — rastreado como
> **EST-C001** em [`estoque`](../estoque/README.md#backlog-do-módulo), porque o sintoma
> aparece no fluxo de baixa de estoque.

## Histórico de Implementações

- **2026-08-18** — `comanda-de-mesa-para-lounge` (**PDV-F009**): pedidos incrementais numa sessão
  de caixa aberta por horas — o caso do lounge de narguilé, distinto da venda pontual de balcão
  que `Order`/`registerSale` já cobrem. Endpoints novos (`/pdv/comandas`), sem mexer em
  `POST /pdv/sessions/{id}/sales`. Novo `Comanda`/`ComandaItem` (`core/domain/model/pdv`) — **não
  reaproveita `OrderItem`**: aquele exige `fromCatalog` como único caminho de construção, pensado
  para venda atômica única, não para acumulação incremental por horas com preço congelado por
  linha. **Baixa de estoque imediata por item**, não só no fechamento — reflete o evento físico
  real (a essência foi preparada e servida), com a contrapartida deliberada de não ser
  transacionalmente atômica ao longo da vida da comanda: cada `addItem` é seu próprio commit (não
  dá para segurar uma transação de banco aberta por horas), e itens já lançados não fazem rollback
  se um lançamento posterior falhar. `POST /pdv/comandas/{id}/cancel` cobre o abandono explícito
  (devolve cada item via `ENTRADA`, mesmo padrão de `OrderService.refundOrder`), mas não há
  varredura automática para comanda esquecida aberta sem cancelamento — **limitação conhecida**,
  no mesmo espírito de PLAT-C035. No fechamento, cada `ComandaItem` vira `OrderItem` via `of()`
  (reconstituição), nunca via `fromCatalog` de novo — repreçar no fechamento repreçaria em
  silêncio itens que o cliente já consumiu, se o catálogo mudou nas horas em que a comanda ficou
  aberta. Sem desconto por item nesta entrega (fora de escopo). **`ComandaService` injeta o bean
  concreto `PdvService`** (não a interface `PdvUseCase`, que esconderia membros package-private)
  para reaproveitar `requireOwnOpenSession`/`validatePaymentsAndComputeChange` sem duplicar a
  regra de troco em pagamento dividido, que já foi endurecida uma vez — primeira dependência
  service-para-service do projeto, deliberada; `CoreBeanConfig` expõe `PdvService` como bean
  concreto além da interface `PdvUseCase` para viabilizar isso. Permissão nova
  `PDV_COMANDA_MANAGE` (não reaproveita `PDV_SALE_MANAGE` — superfície operacional diferente,
  tab de horas vs. venda pontual); leitura sob `PDV_READ`. Migrations V104 (`comanda`/
  `comanda_item`, com `CHECK` de coexistência status↔`closed_at`↔`order_id` espelhando o compact
  constructor de `Comanda`) e V105 (seed da permissão). Cobertura: `ComandaTest`/`ComandaItemTest`
  (domínio), `ComandaServiceTest` (Mockito, incl. ordem de validação e não-repreçamento no
  fechamento), `ComandaRepositoryIT` (round-trip), `ComandaCashCycleIT` (ciclo completo contra
  banco real, incl. baixa verificada a cada item e devolução no cancelamento),
  `PdvComandaControllerTest`/`PdvComandaControllerSecurityTest`, mais os casos novos em
  `SeedConfigTest`/`DevRoleBootstrapConfigTest`. Backend-only: feature que o `mahal-admin` nunca
  pediu — anunciada em `Docs/BACKEND_TODO.md` daquele repo para o time do admin planejar a UI.
- **2026-08-18** — `auditar-e-documentar-o-modulo` (**PDV-C001**): README ganhou as três seções
  que faltavam — Regras de Negócio Implementadas (tabela regra→código→teste, agrupada por
  sub-área: ciclo de caixa, venda de balcão, liquidação online, máquina de estados, item do
  pedido, pagamento, cancelamento/reembolso, concorrência), Schema de Banco (um bloco por
  migration de V57 a V100) e Cobertura de Testes (as 16 classes de teste do módulo). Nenhuma
  mudança de código — auditoria pura, extraída diretamente de `PdvService`/`OrderService`/
  domínio/migrations/testes já existentes. Duas lacunas reais ficaram documentadas em vez de
  maquiadas como "tudo coberto": ausência de IT dedicado para `CashRegisterSession`/`CashMovement`
  (cobertura só indireta via `PdvCashCycleIT`) e **PLAT-C035** (a garantia "uma sessão aberta por
  operador" depende de índice parcial que o H2 do perfil `dev` não suporta — só Postgres protege,
  nunca testado sob concorrência real). De passagem, corrigida uma nota desatualizada na seção de
  Postman que ainda descrevia a abertura de caixa como "sem endpoint" (era verdade antes de
  PDV-F001, 2026-07-28).
- **2026-08-17** — `reserva-para-retirada` (**PDV-F008**): novo status `RESERVADO` no meio do
  caminho entre pagamento capturado e retirada da mercadoria. A leitura inicial do pedido ("pago,
  aguardando retirada") sugeria `PAGO → RESERVADO`, mas o código real não passa por aí — uma venda
  de balcão nunca fica persistida em `PAGO` (`PdvService.registerSale` monta o pedido em memória
  como `CRIADO` e grava uma vez só, direto como `CONCLUIDO`). O ramo novo é `CRIADO → RESERVADO`,
  paralelo ao `CRIADO → CONCLUIDO` que já existia, decidido dentro do mesmo `registerSale` por um
  novo parâmetro `reserveForPickup` (`SaleRequest.reserveForPickup`, default `false`, sem quebra de
  contrato). Isso também resolve sozinho a restrição "só balcão": `CRIADO` só é produzido por
  `Order.openBalcao` — um pedido de marketplace nunca alcança `RESERVADO` por não passar por
  `CRIADO`, sem nenhuma checagem de canal em código. Novo `Order.reserved(orderNumber,
  changeAmount, reservedAt)` (espelho de `concluded`, consome a numeração fiscal do mesmo jeito) e
  `Order.pickedUp(concludedAt)` para a retirada (`RESERVADO → CONCLUIDO`, via
  `POST /orders/{id}/status`, mesmo endpoint da esteira de fulfillment) — dedicado porque o
  `withStatus` genérico não carimba timestamp nenhum, e sem ele `concludedAt` ficaria nulo para
  sempre num pedido retirado. Novo campo `reservedAt`, histórico como `paidAt` — sobrevive à
  retirada, sem `CHECK` de coexistência com o status atual (diferente de `cancelledAt`/
  `refundedAt`). `RESERVADO → REEMBOLSADO` cobre o cliente que nunca voltou: `OrderService.
  refundOrder` já era genérico o bastante para funcionar sem nenhuma mudança, e `RESERVADO` entrou
  na *whitelist* de status "com pagamento confirmado" usada pelas agregações de receita
  (`OrderJpaRepository`/`OrderItemJpaRepository`, 5 ocorrências) — sem isso, uma venda reservada
  ficaria fora do faturamento do período até ser retirada, subestimando a receita real. Migration
  V98 (`ck_sales_order_status` + coluna `reserved_at`). `reservedAt` também exposto em
  `OrderResponseDTO`/`OrderAdminResponseDTO`, para a tela de Reservas do admin mostrar desde
  quando. Cobertura: `OrderStatusTest` (transições novas e a prova de inalcançabilidade do
  marketplace), `OrderTest` (`reserved`/`pickedUp`), `PdvServiceTest`, `OrderServiceTest`
  (`pickedUp` vs. `withStatus`), `PdvControllerTest`, `OrdersControllerTest` e `PedidoRepositoryIT`
  (round-trip de `reservedAt`, filtro por status, retirada preservando o histórico).
- **2026-07-29** — `cancelamento-e-reembolso-do-pedido` (**PDV-F007**, Fatia 5): `OrderStatus`
  ganha `REEMBOLSADO`, terminal e distinto de `CANCELADO` — exatamente como pedido com o dono em
  2026-07-28 ("cancelar e reembolsar são eventos diferentes"), não uma fusão dos dois. A máquina
  fica estritamente partida: pré-pagamento (`CRIADO`/`AGUARDANDO_PAGAMENTO`) só alcança
  `CANCELADO`; pós-pagamento (`PAGO` em diante) só alcança `REEMBOLSADO`. `cancelOrder` deixou de
  devolver estoque via `adjustStock` — nunca houve baixa real para desfazer, só reserva — e passou
  a chamar `EstoqueUseCase.releaseReservationsByOwner` (já existente, idempotente). Novo
  `refundOrder` (`POST /orders/{id}/refund`, permissão nova `ORDER_REFUND`) devolve a mercadoria ao
  estoque (o EST-F014 que antes vivia em `cancelOrder`), estorna cada pagamento `CAPTURED` com uma
  linha `REFUNDED` nova do mesmo método/valor (nunca um update, mesma regra append-only do resto do
  ledger) e reverte todo ganho `EARNED` ainda não revertido no ledger de cashback. Migrations V71
  (`refunded_at`, `CHECK` de status) e V72 (`ORDER_REFUND`). Efeito colateral corrigido de
  passagem: `sumPendingByCustomerId` (domínio `crm`) não excluía ganho já revertido, então um
  reembolso feito durante a carência continuava contando como pendente. Coberto por
  `OrderStatusTest`, `OrderTest`, `OrderServiceTest`, `OrderPaymentTest`, `CashbackEntryTest`,
  `CashbackServiceTest` e o novo `OrderRefundIT`.
- **2026-07-29** — `pagamento-multiplas-formas-e-troco` (**PDV-F006**): nova tabela `order_payment`
  (V68, um port próprio em `core/ports/out/pagamento`) — uma linha por forma, balcão grava direto
  em `CAPTURED` porque o dinheiro já está na gaveta no instante da venda. `POST
  /pdv/sessions/{id}/sales` passa a exigir `payments` (pelo menos uma linha); a soma tem que cobrir
  o líquido do pedido, validada **antes** de tocar o estoque. **Regra de troco, mais estrita que o
  desenho original do plano (§2.6):** a parte que não é `DINHEIRO` não pode, sozinha, passar do
  líquido — só dinheiro pode ser tendido a mais — o que fecha um caso de pagamento dividido em que
  a fórmula original do plano (`soma DINHEIRO − total`) calcularia um troco menor que o real. Troco
  continua sendo `change_amount` no pedido, nunca uma linha de pagamento negativa. O fechamento de
  caixa passou a somar só `DINHEIRO` no `expectedAmount` — débito, crédito e PIX se conferem contra
  a adquirente, não contra o contado na gaveta — e ganhou `GET /pdv/sessions/{id}/payment-totals`
  com o total por forma. Também entrou `GET /pdv/sales/{id}/receipt`: comprovante interno **não
  fiscal** (itens, valores, formas de pagamento) para a loja imprimir/exportar até a NFC-e (Fatia
  11) existir. Índice único em `gateway_ref` desde já — muito antes do gateway (Fatia 10) existir,
  por decisão de risco do próprio plano. Coberto por `OrderPaymentTest`, os novos casos de
  `PdvServiceTest`, `PdvControllerSecurityTest` e `PdvCashCycleIT`
  (`splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer`).
- **2026-07-28** — `ciclo-de-caixa` (**PDV-F001**, **PDV-F002**, **PDV-C002**, **PDV-C004**):
  `CashRegisterSession` deixou de ser stub e ganhou invariantes, `open`/`closedWith`/`diverges`;
  `CashMovement` + `CashMovementType` como ledger append-only, com o sinal vindo do tipo e não do
  valor; migration V66 com as colunas de conferência, a tabela `cash_movement` e o índice parcial
  único por operador. Seis endpoints novos de sessão. O fechamento espelha `StockCount`: confronta
  contado × esperado, carimba a divergência e **fecha mesmo assim**. `PDV_SESSION_CLOSE` é separada
  de `PDV_SESSION_MANAGE` porque a conferência é do gerente — é a única operação da sessão que não
  exige posse. `GET /pdv/sessions` passou a devolver DTO (PDV-C002), e a venda passou a herdar o
  depósito da sessão e a exigir posse dela (PDV-C004). Coberto por `CashRegisterSessionTest`,
  `CashMovementTest`, `PdvServiceTest`, `PdvControllerSecurityTest` e `PdvCashCycleIT`.
- **2026-07-28** — `fundacao-do-pedido` (**PDV-F003**, **PDV-F004**, **PDV-F005**): `Sale`/`SaleItem`
  foram **substituídos** por `Order`/`OrderItem` em `core/domain/model/pedido`, com discriminador de
  canal e máquina de estados; migration V65 renomeia `cash_register_sale → sales_order`. O preço e o
  custo passam a vir do catálogo (`OrderItem.fromCatalog`), com `unitPrice` fora do request e
  `discountAmount` sob `PDV_SALE_DISCOUNT`; o item congela `unit_price`, `cost_price` e
  `cashback_percent`. Numeração de sequência própria emitida na conclusão. A venda deixou de ser
  write-only: `GET /pdv/sales/{id}` e `GET /pdv/sessions/{id}/sales`. Coberto por `OrderTest`,
  `OrderItemTest`, `OrderStatusTest` e `PedidoRepositoryIT`.

- **2026-07-23** — `baixa-automatica-venda` (EST-F010): `Sale`/`SaleItem` com `subtotal()`, `POST /pdv/sessions/{id}/sales` chamando `EstoqueUseCase.adjustStock` com `MovementType.SAIDA` por item e disparando o alerta de reposição; RBAC `PDV_SALE_MANAGE`; migration V57 (`cash_register_session`, `cash_register_sale`, `sale_item`). Coberto por `PdvServiceTest`, `SaleTest` e `PdvControllerSecurityTest`. Commit `deed2d2`.

## Próximos passos

Roteiro completo — ordem, decisões já tomadas e armadilhas — em
[`proximos-passos.md`](proximos-passos.md), que inclui um prompt pronto para colar numa sessão
nova. Resumo da ordem (§6 do [plano](../../plano-pdv-marketplace.md)):

- [ ] **PDV-F003 + PDV-F004 + PDV-F005** — Fatia 0: fundação do pedido. Vem antes de tudo, inclusive
      do ciclo de caixa, porque é a única mudança cujo custo cresce com o volume de vendas gravadas.
- [x] **PDV-F001 + PDV-F002 + PDV-C004** — Fatia 1: ciclo de caixa. Fechado em 2026-07-28.
- [x] **PDV-F006** — Fatia 3: pagamento com múltiplas formas e troco. Fechado em 2026-07-29.
- [ ] **PDV-C001** — auditar o código e completar este README.
