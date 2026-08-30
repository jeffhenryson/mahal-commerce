# Domínio: vendas-balcao (PDV — Frente de Caixa)

**Status:** 🟢 Operacional — ciclo de caixa completo (abertura, sangria/suprimento, fechamento com conferência por forma de pagamento), venda com preço vindo do catálogo, pagamento com múltiplas formas, troco e comprovante interno (PDV-F006, Fatia 3), comanda de mesa para consumo incremental de horas (PDV-F009) e sessão de narguilé na mesa com canal próprio `MESA`, modo de consumo, cortesia e open rosh (PDV-F010), com registro do setup da mesa e acréscimo manual no open rosh (PDV-F011), desconto no fechamento e taxa de serviço (PDV-F014/F015).
**Pacote Java:** `com.cernecommerce...pdv` (packages não aceitam hífen; `pdv` ↔ `vendas-balcao`)
**Rota HTTP base:** `/pdv`
**Última atualização deste doc:** 2026-08-30 — **PDV-C016**: os dois descontos impossíveis ganharam
código de erro próprio. Desconto de item acima do bruto da linha (balcão) e desconto acima da conta
(mesa) subiam como `IllegalArgumentException` → **400 genérico** (`BAD_REQUEST`, "Requisição
inválida"), indistinguível de qualquer corpo malformado; na mesa o rateio ainda roda **antes** do
teto, então o desconto absurdo nem chegava ao `409 DISCOUNT_LIMIT_EXCEEDED` que a tela já trata.
Agora são `409 ITEM_DISCOUNT_EXCEEDS_GROSS` e `409 DISCOUNT_EXCEEDS_BILL`. Sem migration. Na mesma
data, antes: **PDV-C015 + PDV-C017 +
PDV-C018**: o esperado do
fechamento de caixa passou a contar o que **sai** da gaveta, não só o que entra. Eram três buracos
na mesma fórmula: o **troco** devolvido nunca era subtraído (a linha de pagamento guarda o valor
*entregue* pelo cliente), o **estorno** também não (o ledger é append-only e a `CAPTURED` original
fica de pé), e a **liquidação de pedido online** não gravava pagamento nenhum — dinheiro entrando
sem o ledger saber. Migration V120. **Quebra de contrato:** `POST .../settle` passou a exigir corpo.
Na mesma data, antes: **PDV-F012 + PDV-C013 + PDV-C014**: remoção de linha
de comanda (com a `TROCA` arrastada e o `SABOR_EXTRA` barrando), ordenação na paginação de sessões e
`EventType` próprio de comanda no lugar do `STOCK_MOVEMENT_REGISTERED` emprestado. Sem migration.
Na mesma data, antes: **PDV-F014 + PDV-F015**: desconto no fechamento de
mesa (rateado entre os itens) e taxa de serviço (em coluna própria, **fora** do `netAmount`, para a
gorjeta não virar receita da casa). Migrations V118/V119. Na mesma data, mais cedo: **PDV-C007 +
C009 + C010 + C011 + C012**, a superfície de listagem de mesas — `GET /pdv/comandas` deixou de
exigir `sessionId` e passou a listar a loja inteira, com o teto de paginação e o `JOIN FETCH` na
mesma mudança; duas rotas mudaram de contrato (`List` → `PageResult`).

<details><summary>Atualizações anteriores</summary>

**2026-08-28** (duas rodadas de `/1-analise` no mesmo dia. A
**primeira** documentou **PDV-F010** a posteriori — endpoints, RBAC, regras, schema V104/V111–V115
e testes de comanda, que estavam no código desde 2026-08-27 e nunca chegaram aqui — e abriu seis
itens de backlog, entre eles **PDV-C005** 🔴 e **PDV-F011**, o pedido aberto do
`frontend-admin-prod`. A **segunda** varreu o adapter de persistência, os DTOs de resposta e as
validações de request, camadas que a primeira não tinha alcançado: onze itens novos, sendo
**PDV-C008** o achado 🔴 — a comanda é o único agregado mutável do módulo sem `@Version`, logo
depois de PDV-F010 ter liberado dois atendentes na mesma mesa. **2026-08-29:** os três 🔴 que
aquelas duas rodadas abriram — PDV-C005, PDV-F011 e PDV-C008 — foram implementados e documentados
aqui, migrations V116/V117. Da correção de PDV-C008 saiu **PLAT-C047**, roteado à `plataforma`.)

</details>

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
| domain/model | `core/domain/model/pdv/` — `CashRegisterSession`, `CashMovement`/`CashMovementType`, `Comanda`/`ComandaItem`/`ComandaStatus`. O pedido em si vive em `core/domain/model/pedido/` (`Order`, `OrderItem`, `OrderStatus`, `SalesChannel`, `ConsumptionMode`) — `Sale`/`SaleItem` foram substituídos em PDV-F003/F004/F005 |
| ports/in | `core/ports/in/PdvUseCase`, `core/ports/in/ComandaUseCase` |
| ports/out | `core/ports/out/pdv/` — `CashRegisterRepository`, `CashMovementRepository`, `ComandaRepository`; `core/ports/out/pedido/OrderRepository`; `core/ports/out/pagamento/OrderPaymentRepository` |
| service | `core/service/PdvService` e `core/service/ComandaService` (wired em `CoreBeanConfig`). `ComandaService` recebe o bean **concreto** `PdvService` — ver o Histórico de PDV-F009 |
| adapter/in | `adapter/in/controller/PdvController` (balcão e ciclo de caixa) e `adapter/in/controller/PdvComandaController` (`/pdv/comandas`), com `ComandaDTOConverter` |

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/pdv/sessions` | `PDV_READ` | Lista sessões de caixa paginadas (`page` ≥ 0, `size` 1–100), **das mais recentes para as mais antigas** — a ordenação entrou em PDV-C013; antes a paginação não era determinística |
| `POST` | `/pdv/sessions` | `PDV_SESSION_MANAGE` | Abre o caixa. Uma sessão aberta por operador; o depósito informado vale para todas as vendas dela |
| `GET` | `/pdv/sessions/current` | `PDV_READ` | Caixa aberto do operador autenticado |
| `GET` | `/pdv/sessions/{id}` | `PDV_READ` | Detalhe da sessão |
| `POST` | `/pdv/sessions/{id}/movements` | `PDV_SESSION_MANAGE` | Sangria ou suprimento. Exige sessão aberta **e do próprio operador** |
| `GET` | `/pdv/sessions/{id}/movements` | `PDV_READ` | Movimentos da sessão, paginados (`page` ≥ 0, `size` 1–100), na ordem de lançamento (PDV-C012) |
| `POST` | `/pdv/sessions/{id}/close` | `PDV_SESSION_CLOSE` | Fecha confrontando contado × esperado. **Divergência não bloqueia** — mas **mesa aberta sim** (PDV-C005): `409 SESSION_HAS_OPEN_COMANDAS`, checado antes de calcular o esperado |
| `GET` | `/pdv/sessions/{id}/payment-totals` | `PDV_READ` | Total recebido na sessão por forma de pagamento — só `CAPTURED` conta. As quatro formas sempre aparecem, mesmo zeradas |
| `GET` | `/pdv/pending-online-orders` | `PDV_READ` | Pedidos do app aguardando pagamento, para o caixa localizar quem chegou na loja |
| `POST` | `/pdv/sessions/{id}/orders/{orderId}/settle` | `PDV_SALE_MANAGE` | Liquida no balcão um pedido do app: consome a reserva, **registra o pagamento recebido** e conclui. **Corpo obrigatório desde PDV-C015** (`payments`, mesmo shape da venda de balcão), somando **exatamente** o líquido — aqui não há onde guardar troco. Encerra a cobrança de gateway aberta no checkout. Erros: `400 INSUFFICIENT_PAYMENT`, `400 CHANGE_NOT_SUPPORTED` |
| `POST` | `/pdv/sessions/{id}/sales` | `PDV_SALE_MANAGE` (+ `PDV_SALE_DISCOUNT` se houver desconto) | Registra venda na sessão, **captura o pagamento** e **dá baixa no estoque** item a item. Preço e custo vêm do catálogo, não do request. Exige sessão `OPEN` e ao menos uma linha em `payments`. Desconto de linha acima do bruto dela: `409 ITEM_DISCOUNT_EXCEEDS_GROSS` (PDV-C016 — era um 400 genérico) |
| `GET` | `/pdv/sales/{id}` | `PDV_READ` | Consulta um pedido, com os pagamentos. Antes de PDV-F005 a venda era write-only |
| `GET` | `/pdv/sales/{id}/receipt` | `PDV_READ` | Comprovante interno da venda — **não é documento fiscal** (isso é a NFC-e, Fatia 11) |
| `GET` | `/pdv/sessions/{id}/sales` | `PDV_READ` | Pedidos da sessão, paginados, do mais recente para o mais antigo |
| `POST` | `/pdv/comandas?sessionId=` | `PDV_COMANDA_MANAGE` | Abre comanda de mesa (PDV-F009). Controller próprio (`PdvComandaController`). Exige a **própria** sessão: a mesa nasce na gaveta de quem a abriu, e é esse depósito que vai baixar estoque. Aceita `customerId` opcional (PDV-F010) — é ele que faz o pedido da mesa sair com nome e gerar cashback |
| `POST` | `/pdv/comandas/{id}/items` | `PDV_COMANDA_MANAGE` (+ `PDV_COMANDA_COURTESY` se a linha for cortesia, + `PDV_COMANDA_SURCHARGE` se houver acréscimo positivo) | Lança item na comanda aberta — **debita estoque na hora**, não no fechamento. Aceita `mode` (`NORMAL`/`OPEN_ROSH`/`SABOR_EXTRA`/`TROCA`), `courtesy` e `linkedItemId` (PDV-F010), mais `notes` (máx. 200, sem efeito em preço) e `surchargeAmount` (só em `OPEN_ROSH`, soma sobre o `openRoshPrice` do produto **pai**) (PDV-F011). Erros: `400 NOT_AVAILABLE_FOR_TABLE`, `400 NOT_A_SESSION_PRODUCT`, `400 OPEN_ROSH_NOT_PRICED`, `400 LINKED_ITEM_REQUIRED`, `403 COURTESY_NOT_ALLOWED`, `409 NOT_AN_OPEN_ROSH`, `400 NOTES_TOO_LONG`, `403 SURCHARGE_NOT_ALLOWED`, `400 SURCHARGE_ON_COURTESY`, `400 SURCHARGE_NOT_APPLICABLE`, `400 SURCHARGE_INVALID` |
| `DELETE` | `/pdv/comandas/{id}/items/{itemId}` | `PDV_COMANDA_MANAGE` | Remove uma linha da comanda aberta, devolvendo o estoque dela (`ENTRADA`) — PDV-F012. **As `TROCA` penduradas nela saem junto**; um `SABOR_EXTRA` pendurado **barra** a remoção (`409 LINKED_ITEM_IS_CHARGED`), porque é linha própria e pode estar cobrada. Erros: `404 COMANDA_ITEM_NOT_FOUND`, `409 COMANDA_NOT_OPEN` |
| `GET` | `/pdv/comandas/{id}` | `PDV_READ` | Detalhe da comanda, com o total corrente (`runningTotal`), o cliente (`customerId`/`customerName`, resolvido no CRM) e o `mode`/`courtesy`/`linkedItemId` de cada linha |
| `GET` | `/pdv/comandas` | `PDV_READ` | As "mesas ocupadas". **`sessionId` é opcional desde PDV-C007**: sem ele a listagem é da **loja inteira**, que é o que a decisão de mesas compartilhadas pede — era a obrigatoriedade do parâmetro que forçava o cliente ao merge N+1 de uma chamada por sessão. Aceita também `warehouseCode`, e pagina (`page` ≥ 0, `size` 1–100, default 50). **Não checa posse.** Devolve `PageResult` — ver a nota de contrato abaixo |
| `POST` | `/pdv/comandas/{id}/close` | `PDV_COMANDA_MANAGE` (+ `PDV_COMANDA_DISCOUNT` se `discountAmount > 0`) | Fecha a comanda: converte os itens acumulados num pedido concluído que **nasce `channel = MESA`**, com `comandaId` e `tableLabel`. Mesmo contrato de pagamento de `POST /pdv/sessions/{id}/sales`; **sem novo débito de estoque** — já saiu item a item. O pedido entra na sessão de **quem fecha**, não na que abriu. Aceita `discountAmount` (abatimento na **conta inteira**, rateado entre as linhas pelo servidor — PDV-F014) e `applyServiceFee` (taxa de serviço, **`true` por omissão** — PDV-F015). O pagamento é validado contra `netAmount + serviceFeeAmount`. Erros: `409 COMANDA_EMPTY`, `409 COMANDA_ONLY_COURTESY`, `409 NO_OPEN_CASH_REGISTER_SESSION`, `409 DISCOUNT_LIMIT_EXCEEDED`, `409 DISCOUNT_EXCEEDS_BILL` (PDV-C016 — era um 400 genérico), `403 COMANDA_DISCOUNT_NOT_ALLOWED` |
| `GET` | `/pdv/comandas/service-fee` | `PDV_READ` | Percentual da taxa de serviço vigente (PDV-F015), para a tela mostrar o valor **antes** de fechar. Zero significa que a casa não cobra |
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

> **Contrato alterado em PDV-C007/C012** (2026-08-30), este **com** consumidor real: `GET /pdv/comandas`
> e `GET /pdv/sessions/{id}/movements` devolviam a `List` na raiz do corpo e passaram a devolver
> `PageResult` — os itens saíram da raiz para `content`. Mesma quebra, e mesma justificativa, de
> EST-C005 em `GET /estoque/warehouses`: uma listagem sem teto é um incidente esperando o volume
> chegar, e a de mesas ia justamente deixar de ser contida por um caixa só. O `frontend-admin-prod`
> regenera o client do `openapi.json` e troca o acesso ao array.

> **Contrato alterado em PDV-C015** (2026-08-30), **com consumidor real**:
> `POST /pdv/sessions/{id}/orders/{orderId}/settle` **não tinha corpo** e passou a exigir
> `{ "payments": [...] }`, o mesmo shape de `POST /pdv/sessions/{id}/sales`. A rota concluía o
> pedido sem registrar como o dinheiro entrou — era o único caminho de recebimento do projeto fora
> do ledger de pagamento. O `frontend-admin-prod` já gera cliente para ela
> (`src/app/api/fn/pdv-vendas-balcao/settle-online-order.ts`): regenerar do `openapi.json` e passar
> o corpo. **Atenção ao valor exato:** o canal continua `MARKETPLACE` e pedido de marketplace não
> admite `changeAmount` — a tela tem que lançar o que fica na gaveta, não a cédula entregue, e
> excedente responde `400 CHANGE_NOT_SUPPORTED`.

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
| `expectedAmount` = abertura + `DINHEIRO` capturado **− troco devolvido − estorno em dinheiro** − sangrias + suprimentos | `PdvService.closeSession` | `PdvServiceTest.closeSession_computesExpectedFromOpeningCashSalesAndMovements` |
| **O troco sai do esperado** (PDV-C017) — `order_payment.amount` em `DINHEIRO` é o valor **entregue** pelo cliente, não o retido (é assim que `validatePaymentsAndComputeChange` deriva o troco). Sem subtrair, toda venda em dinheiro com nota quebrada inflava o esperado e o turno fechava acusando uma **falta** que era só aritmética | `PdvService.closeSession` → `OrderRepository.sumChangeAmountBySessionId` | `PdvServiceTest.closeSession_subtractsTheChangeGivenBackFromTheExpectedAmount`, `PdvCashCycleIT.splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer` |
| **O estorno sai do esperado** (PDV-C018) — o ledger é append-only: `refundOrder` grava uma linha `REFUNDED` nova e **deixa a `CAPTURED` de pé**, que é o desenho certo para o histórico. Por isso a subtração precisa de consulta própria: a soma de capturados descreve tudo que entrou e nada do que voltou | `PdvService.closeSession` → `OrderPaymentRepository.sumRefundedAmountBySessionIdAndMethod` | `PdvServiceTest.closeSession_subtractsRefundedCashFromTheExpectedAmount`, `PdvCashCycleIT.refundedSaleTakesTheCashBackOutOfTheExpectedAmount` |
| A soma do troco **não filtra status do pedido**: a cédula saiu da gaveta na venda e não volta — `RESERVADO` já devolveu troco, e `REEMBOLSADO` devolve ao cliente o valor *entregue* (é o `amount` da `CAPTURED` que `refunded` espelha). Filtrar por `CONCLUIDO` devolveria o troco ao esperado no instante do reembolso | `OrderJpaRepository.sumChangeAmountBySessionId` | `PdvCashCycleIT.refundedSaleTakesTheCashBackOutOfTheExpectedAmount` |
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
| Desconto de **linha** acima do bruto dela é 409 `ITEM_DISCOUNT_EXCEEDS_GROSS` (PDV-C016) — não confundir com o teto: aqui o valor é aritmeticamente impossível, independente de política. Antes subia como `IllegalArgumentException` → **400 `BAD_REQUEST` genérico**, que descarta a mensagem do domínio. A recusa é tipada em `fromCatalog` porque **ali o valor veio do cliente HTTP**; a checagem do compact constructor continua onde está, como rede contra erro de programação e dado corrompido | `OrderItem.fromCatalog` (`ItemDiscountExceedsGrossException`) | `OrderItemTest.rejectsDiscountGreaterThanGross`, `of_stillRejectsDiscountGreaterThanGrossAsAnInvariant`, `PdvServiceTest.registerSale_refusesItemDiscountAboveTheLineGrossBeforeTouchingStock` |
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
| **O pagamento recebido é registrado** (PDV-C015) — era o único caminho de recebimento do projeto que não gravava linha de pagamento. Sem ela `closeSession` (que soma `order_payment`, não pedido) não esperava a cédula, e o turno fechava acusando **sobra** sem dono; `/payment-totals` não via o valor e o comprovante saía com pagamentos vazios | `PdvService.settleOnlineOrder` → `OrderPayment.captured` | `PdvServiceTest.settleOnlineOrder_recordsTheCapturedPaymentInTheReceivingSession`, `PdvCashCycleIT.settledOnlineOrderPaidInCashCountsTowardsTheExpectedAmount` |
| Pagamento é validado **antes** de consumir a reserva — mesma ordem de `registerSale`: recusa não deveria custar uma reserva consumida que só o rollback desfaz | `PdvService.settleOnlineOrder` (ordem das checagens) | `PdvServiceTest.settleOnlineOrder_refusesInsufficientPaymentBeforeConsumingTheReservation` |
| **Valor exato, sem troco** — o canal permanece `MARKETPLACE` e `Order` recusa `changeAmount` ali (`ck_sales_order_change_amount_by_channel`). Aceitar o excedente sem ter onde gravá-lo faria a linha de pagamento afirmar que entrou na gaveta mais do que ficou: o defeito de PDV-C017 voltando por outra porta | `PdvService.settleOnlineOrder` (`ChangeNotSupportedException`, 400) | `PdvServiceTest.settleOnlineOrder_refusesPaymentAboveTheNetAmountBecauseThereIsNowhereToPutChange` |
| A cobrança de gateway aberta no checkout é **encerrada** (`CANCELLED`), não deixada pendurada — `ShopService.checkout` grava uma `PENDING`/`GATEWAY_PIX` em todo pedido de marketplace, e pago no balcão nenhum webhook vai confirmá-la. Atualiza a **mesma** linha (exceção documentada ao append-only, como `confirmCaptured`): uma linha nova ao lado deixaria a `PENDING` de pé, que é o que o passo existe para não deixar | `PdvService.cancelPendingGatewayCharges` → `OrderPayment.cancelled()` | `PdvServiceTest.settleOnlineOrder_cancelsThePendingGatewayChargeFromCheckout` |
| Efeito colateral desejado: um webhook atrasado para pedido já liquidado no balcão vira **no-op sem chamada externa** — `PaymentWebhookService.findPendingPayment` não acha mais `PENDING` e retorna antes de reconsultar o gateway | `PaymentWebhookService` (inalterado) | — |
| **Comanda de mesa (PDV-F009 / PDV-F010)** | | |
| Abrir comanda exige a **própria** sessão — a mesa nasce na gaveta de quem a abriu, e é esse depósito que baixa estoque | `ComandaService.openComanda` (`requireOwnOpenSession`) | `ComandaServiceTest.openComanda_opensAtTheSessionWarehouse`, `openComanda_propagatesOwnershipFailureAndDoesNotSave` |
| Lançar/fechar/cancelar mesa **não** exige posse do caixa: o consumo é do salão, não do operador. O controle é a permissão, não a gaveta | `ComandaService.addItem`/`cancelComanda` → `PdvService.requireOpenSession` | `ComandaServiceTest.closeComanda_creditsTheSessionOfWhoeverCloses` |
| Estoque é debitado **no lançamento**, não no fechamento — reflete o evento físico (a essência foi servida) | `ComandaService.addItem` → `EstoqueUseCase.adjustStock(SAIDA)` | `ComandaServiceTest.addItem_resolvesPriceFromCatalogAndDebitsStockImmediately`, `ComandaCashCycleIT.fullCycle_openAddTwoItemsWithImmediateDebitAndCloseWithSplitPayment` |
| Todas as validações acontecem **antes** de tocar o estoque — cada lançamento é seu próprio commit, então débito seguido de recusa deixaria saldo baixado sem linha na comanda | `ComandaService.addItem` (ordem das checagens) | `ComandaServiceTest.addItem_refusesSkuNotAvailableForTableBeforeTouchingStock`, `addItem_propagatesInsufficientStockAndDoesNotSave` |
| SKU sem `availableForTable` não entra na mesa — a regra "bebida e narguilé saem na mesa, cigarro não" existe **no servidor**, não só no cliente | `ComandaService.addItem` (`NotAvailableForTableException`) | `ComandaServiceTest.addItem_refusesSkuNotAvailableForTableBeforeTouchingStock` |
| Modo de sessão exige produto marcado `sessionProduct` | `ComandaService.addItem` (`NotASessionProductException`) | `ComandaServiceTest.addItem_refusesSessionModeOnProductThatIsNotASession` |
| **Open rosh cobra o `openRoshPrice` do produto PAI, nunca o preço da variação do sabor** — o SKU da linha está ali para saber qual essência sai do estoque, não para precificar | `ComandaService.resolveUnitPrice` | `ComandaServiceTest.addItem_openRoshChargesParentPriceNotVariantPrice` |
| Open rosh em produto sem `openRoshPrice` é recusado, sem fallback para o preço da variante | `ComandaService.resolveUnitPrice` (`OpenRoshNotPricedException`) | `ComandaServiceTest.addItem_refusesOpenRoshOnProductWithoutOpenRoshPrice` |
| `SABOR_EXTRA` sem promo cobra o preço da **sua própria** variante — é linha própria, não adendo à primeira | `ComandaService.resolveUnitPrice` | `ComandaServiceTest.addItem_saborExtraWithoutPromoChargesItsOwnVariantPrice` |
| Cortesia grava `unitPrice = 0` mas **congela o `costPrice` normalmente** — é o que faz a margem mostrar o prejuízo real da promo e do open rosh. Custo nulo ali mentiria | `ComandaItem.forSession` | `ComandaServiceTest.addItem_courtesyRecordsZeroPriceButFreezesCostNormally`, `ComandaItemTest.forSession_courtesyIsFreeForTheCustomerButNotForTheMargin`, `forSession_rejectsUnpricedProductEvenForACourtesyLine` |
| `courtesy` é campo próprio, **não inferido de preço zero** — um desconto de 100% dá o mesmo zero, e a margem precisa distinguir os dois | `ComandaItem` (compact constructor), `CHECK ck_comanda_item_courtesy_is_free` | `ComandaItemTest.courtesyMustCostZero`, `ComandaRepositoryIT.save_roundTripsCourtesyLineWithFrozenCost` |
| `TROCA` é cortesia **por definição**, mesmo se o cliente HTTP não marcar o campo — troca cobrada seria narguilé vendido duas vezes na mesma sessão de valor fixo | `ConsumptionMode.impliesCourtesy`, `ComandaService.addItem` | `ComandaServiceTest.addItem_trocaIsCourtesyEvenWhenClientDidNotFlagIt`, `PdvComandaControllerTest.addItem_trocaWithoutAuthority_returns_403_evenWithoutTheCourtesyFlag` |
| A linha de origem tem que estar **nesta** comanda — id válido em outra mesa não serve | `ComandaService.resolveLinkedItem` | `ComandaServiceTest.addItem_refusesSaborExtraWithoutLinkedItem`, `addItem_refusesLinkedItemFromAnotherComanda` |
| `TROCA` só se pendura em linha `OPEN_ROSH` — permitir troca cortesia sobre sessão comum daria narguilé de graça | `ComandaService.resolveLinkedItem` (`NotAnOpenRoshException`, 409) | `ComandaServiceTest.addItem_refusesTrocaLinkedToLineThatIsNotOpenRosh` |
| Cortesia exige `PDV_COMANDA_COURTESY` — checagem **programática** no controller, mesma razão de `PDV_SALE_DISCOUNT`: depende do corpo, e o núcleo não conhece Spring Security | `PdvComandaController.requireCourtesyAuthority` | `PdvComandaControllerTest.addItem_courtesyWithoutAuthority_returns_403`, `addItem_withoutCourtesy_doesNotRequireTheAuthority` |
| No fechamento cada `ComandaItem` vira `OrderItem` por **reconstituição** (`of`), nunca `fromCatalog` de novo — repreçar repreçaria em silêncio o que o cliente já consumiu, e no open rosh cobraria o preço do sabor no lugar do valor fixo | `ComandaService.closeComanda` | `ComandaServiceTest.closeComanda_convertsAccumulatedItemsWithoutReQueryingTheCatalog` |
| `mode` e `courtesy` atravessam para o `OrderItem` — sem isso o histórico da mesa não distingue cortesia de item cobrado | `ComandaService.closeComanda` | `ComandaServiceTest.closeComanda_carriesModeAndCourtesyIntoTheOrderItems` |
| Fechamento **não** debita estoque de novo — já saiu item a item | `ComandaService.closeComanda` (sem `adjustStock`) | `ComandaServiceTest.closeComanda_doesNotAdjustStockAgain` |
| O pedido nasce na sessão de **quem fecha** (`getCurrentSession`), não na que abriu a mesa — o dinheiro pertence a quem o recebeu, e isso impede o pedido de cair numa sessão já encerrada | `ComandaService.closeComanda` | `ComandaServiceTest.closeComanda_creditsTheSessionOfWhoeverCloses` |
| Comanda vazia não fecha (`COMANDA_EMPTY`); comanda **só de cortesias** também não (`COMANDA_ONLY_COURTESY`) — total zero é erro de lançamento, e fechá-lo geraria pedido concluído de R$ 0 que ninguém revisaria | `ComandaService.closeComanda` | `ComandaServiceTest.closeComanda_refusesEmptyComandaBeforeTouchingOrders`, `closeComanda_refusesComandaMadeOnlyOfCourtesies` |
| A taxa de cashback vigente é resolvida e **carimbada** no fechamento, como no balcão — sem isso o cliente vinculado na abertura chegaria ao pedido sem ganhar nada | `ComandaService.closeComanda` → `CashbackUseCase.resolveApplicableRate` | `ComandaServiceTest.closeComanda_carriesTheComandaCustomerAndStampsTheCashbackRate`, `closeComanda_semTaxaVigente_fechaSemCashback`, `ComandaCashCycleIT.fullCycle_mesaComClienteEDuploEmCortesia_geraPedidoMesaComCashback` |
| Cancelar devolve ao estoque **cada** item já lançado (`ENTRADA`) — mesmo padrão de `OrderService.refundOrder` | `ComandaService.cancelComanda` | `ComandaServiceTest.cancelComanda_returnsStockPerItem`, `ComandaCashCycleIT.cancelComanda_returnsStockForEveryLaunchedItem` |
| Ids de item são estáveis entre lançamentos, para `linkedItemId` sobreviver ao round-trip | `ComandaEntity`/`ComandaItemEntity` | `ComandaRepositoryIT.save_keepsItemIdsStableAcrossLaunches_soLinkedItemIdSurvives` |
| Comanda só transiciona a partir de `ABERTA`; `FECHADA` exige `orderId` + `closedAt`, `CANCELADA` exige `closedAt` **sem** `orderId` | `Comanda` (compact constructor, `closed`/`cancelled`) | `ComandaTest.closed_refusesToCloseTwice`, `of_rejectsFechadaComandaWithoutOrderIdOrClosedAt`, `cancelled_hasClosedAtButNoOrderId` |
| **Listagem de mesas (PDV-C007 / PDV-C009 / PDV-C012)** | | |
| Sem `sessionId` a listagem é da **loja**, não de um caixa — é o que a decisão "mesas compartilhadas" exige, e o que substitui o merge N+1 do cliente | `ComandaService.listOpenComandas` → `ComandaRepository.findOpen` | `ComandaRepositoryIT.findOpen_withoutSessionId_returnsTheMesasOfEveryCashRegisterSession`, `PdvComandaControllerTest.listOpenComandas_withoutSessionId_listsTheWholeStore` |
| A listagem **não filtra por status da sessão de caixa**, e não precisa: desde PDV-C005 o caixa não fecha com mesa aberta, então comanda `ABERTA` já implica sessão `OPEN`. Um join ali só repetiria uma invariante que o módulo já garante | `ComandaJpaRepository.findOpenIds` (só `status = 'ABERTA'`) | `ComandaRepositoryIT.findOpen_returnsOnlyAbertaComandas` |
| O número de consultas **não cresce com o número de mesas** — ids paginados, depois um `JOIN FETCH` dos itens | `ComandaRepositoryImpl.findOpen` (ID-first) | `ComandaRepositoryIT.findOpen_loadsItemsWithoutOneQueryPerComanda` (conta as consultas via `Statistics` do Hibernate) |
| O nome do cliente é resolvido em **uma** consulta ao CRM para a página inteira — trocar o N+1 de HTTP do cliente por um N+1 de CRM no servidor não seria progresso | `PdvComandaController.enrichCustomerNames` | `PdvComandaControllerTest.listOpenComandas_resolvesCustomerNamesInASingleCrmCall` |
| A guarda de fechamento de caixa tem consulta **própria e não paginada** — uma guarda que enxerga só uma página deixaria um caixa com muitas mesas voltar a fechar com mesa aberta | `ComandaRepository.findOpenIdsBySessionId`, usada por `PdvService.closeSession` | `ComandaRepositoryIT.findOpenIdsBySessionId_returnsEveryOpenMesaOfTheSession` |
| **Remoção de item (PDV-F012)** | | |
| Remover uma linha devolve ao estoque o que ela debitou (`ENTRADA`), e a mesa **continua aberta** — era isso que só saía cancelando a comanda inteira | `ComandaService.removeItem` | `ComandaServiceTest.removeItem_returnsStockForTheLineAndForTheTrocasDraggedWithIt`, `ComandaCashCycleIT.removeItem_returnsStockAndKeepsTheComandaOpen` |
| As `TROCA` penduradas na linha **saem junto**: são cortesia, não existem sem o consumo livre que as originou, e `linked_item_id` é FK auto-referente — deixá-las apontaria para um id que sumiu | `Comanda.withRemovedItem` | `ComandaTest.withRemovedItem_dropsTheLineAndTheTrocasHangingOnIt`, `ComandaCashCycleIT.removeItem_dragsTheTrocaAndReturnsStockForBoth` |
| Um `SABOR_EXTRA` pendurado **barra** a remoção em vez de ser arrastado — é linha própria e pode estar cobrada, e apagá-la em cascata tiraria valor da conta sem o operador pedir | `ComandaService.removeItem` (`LinkedItemIsChargedException`) | `ComandaServiceTest.removeItem_refusesWhenAChargedSaborExtraHangsOnTheLine_beforeTouchingStock` |
| A recusa acontece **antes** de tocar o estoque — cada devolução é seu próprio efeito, e abortar no meio deixaria saldo devolvido sem a linha ter saído | `ComandaService.removeItem` (ordem das checagens) | `ComandaServiceTest.removeItem_refusesWhenAChargedSaborExtraHangsOnTheLine_beforeTouchingStock` |
| Remover é o **quarto** caminho de mutação, e portanto passa pela mesma leitura travada de PDV-C008 | `ComandaService.removeItem` → `getComandaForUpdate` | `ComandaServiceTest.removeItem_readsTheComandaUnderLock` |
| Comanda vazia é estado legítimo — é como ela nasce, e `COMANDA_EMPTY` já barra fechá-la assim | `ComandaService.removeItem` (sem checagem de "última linha") | `ComandaTest.withRemovedItem_canEmptyTheComanda`, `ComandaServiceTest.removeItem_canEmptyTheComanda` |
| **Desconto e taxa no fechamento (PDV-F014 / PDV-F015)** | | |
| O desconto é pedido sobre a **conta**, mas gravado **por item**: é rateado proporcionalmente ao valor de cada linha. Sem o rateio a casa pagaria cashback sobre dinheiro que não recebeu, e a margem por item mostraria a venda cheia | `DiscountProration.distribute`, chamado por `ComandaService.closeComanda` | `DiscountProrationTest` (11 casos), `ComandaServiceTest.closeComanda_proratesTheBillDiscountAcrossTheItems` |
| A soma do rateio bate **no centavo** com o desconto pedido, e nenhuma linha recebe mais desconto que o próprio valor — a sobra do truncamento vai, centavo a centavo, para as linhas com mais folga | `DiscountProration.distribute` | `DiscountProrationTest.distribute_alwaysSumsExactlyToTheRequestedDiscount`, `distribute_neverGivesALineMoreDiscountThanItsOwnAmount` |
| Cortesia absorve zero de desconto **por construção** — a proporção de uma linha de valor zero é zero, sem caso especial | `DiscountProration.distribute` | `ComandaServiceTest.closeComanda_courtesyLineAbsorbsNoDiscount` |
| O teto do desconto é o **mesmo do balcão** (`pdv.sale.max-discount-percent`), e é a checagem do balcão que decide — não uma cópia da regra | `ComandaService.closeComanda` → `PdvService.requireDiscountWithinLimit` | `ComandaServiceTest.closeComanda_checksTheDiscountAgainstTheSameLimitAsTheCounterSale`, `closeComanda_refusesDiscountAboveTheLimitBeforeSavingAnything` |
| Desconto maior que a **conta inteira** é 409 `DISCOUNT_EXCEEDS_BILL` (PDV-C016), checado **antes** do rateio. `DiscountProration.distribute` já recusava, mas com `IllegalArgumentException` → 400 genérico; e por rodar antes de `requireDiscountWithinLimit`, o desconto absurdo nunca chegava ao 409 do teto — **pedir 11% de desconto dava um erro acionável, pedir o dobro da conta dava "Requisição inválida"**. A checagem fica no service, não dentro de `distribute`: aquela é função pura de aritmética, e o vocabulário de erro do PDV não é dela | `ComandaService.closeComanda` (`DiscountExceedsBillException`) | `ComandaServiceTest.closeComanda_refusesDiscountGreaterThanTheBillBeforeProratingAnything`, `PdvComandaControllerTest.closeComanda_discountGreaterThanTheBill_returns_409_withItsOwnErrorCode` |
| **A taxa de serviço fica FORA do `netAmount`** — o líquido é somado como receita em quatro agregações, e a gorjeta é do garçom, não da casa. Somá-la ali inflaria receita e margem com dinheiro que a loja apenas repassa | `Order` (compact constructor), coluna própria `service_fee_amount` | `OrderTest.withServiceFeeOf_leavesNetAmountUntouchedAndOnlyMovesTotalPayable`, `ComandaServiceTest.closeComanda_serviceFeeStaysOutOfTheRevenueFigure` |
| O pagamento e o troco são validados contra `totalPayable()` (= líquido + taxa), não contra o líquido — é o único ponto do módulo em que os dois números diferem | `ComandaService.closeComanda` | `ComandaServiceTest.closeComanda_validatesThePaymentAgainstTotalPayableNotNetAmount`, `ComandaCashCycleIT.fullCycle_mesaComDescontoRateadoETaxaDeServico` |
| A conferência da gaveta continua correta **sem mudança nenhuma**: `closeSession` soma `order_payment`, não `netAmount`, e o dinheiro da taxa passa pela gaveta como qualquer outro | `PdvService.closeSession` (inalterado) | `ComandaCashCycleIT.closeSession_expectedAmountIncludesTheServiceFeePaidInCash` |
| A taxa incide sobre o líquido, portanto **depois** do desconto — cobrar serviço sobre um abatimento recém-concedido seria devolver parte dele com a outra mão | `ComandaService.closeComanda` (ordem das operações) | `ComandaServiceTest.closeComanda_computesTheServiceFeeAfterTheDiscount` |
| A taxa vem **aplicada por padrão**: omitir `applyServiceFee` significa sim. É o padrão do salão, e depender de o atendente lembrar de marcar é o problema que a feature existe para resolver | `CloseComandaRequest.isServiceFeeApplied` | `PdvComandaControllerTest.closeComanda_omittingApplyServiceFee_appliesTheFee`, `closeComanda_applyServiceFeeFalse_isTheCustomerRefusing` |
| Taxa de serviço só existe em `MESA` — no balcão não há serviço a cobrar | `Order` (compact constructor), `CHECK ck_sales_order_service_fee_only_mesa` | `OrderTest.serviceFeeOnlyExistsInMesa` |
| Dar desconto no fechamento exige `PDV_COMANDA_DISCOUNT` — checagem **programática** no controller, mesma razão de `PDV_COMANDA_COURTESY`. Desconto zero/nulo é no-op e **não** exige a permissão | `PdvComandaController.requireComandaDiscountAuthority` | `PdvComandaControllerTest.closeComanda_discountWithoutAuthority_returns_403`, `closeComanda_zeroDiscount_doesNotRequireTheAuthority` |
| **Máquina de estados do pedido (`OrderStatus`/`Order`)** | | |
| Canal determina campo obrigatório: `MARKETPLACE` exige `customerId`, `BALCAO` exige `sessionId` | `Order` (compact constructor) | `OrderTest.marketplaceRequiresCustomer`, `balcaoRequiresSession` |
| `MESA` é canal próprio e **imutável** (PDV-F010): o pedido da mesa tem que **nascer** `MESA`, não virar depois. Carrega `comandaId` e `tableLabel` | `Order.openMesa` | `ComandaServiceTest.closeComanda_carriesModeAndCourtesyIntoTheOrderItems`, `ComandaCashCycleIT.fullCycle_mesaComClienteEDuploEmCortesia_geraPedidoMesaComCashback` |
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
| `PDV_COMANDA_MANAGE` | `POST`/`.../items`/`.../close`/`.../cancel` de `/pdv/comandas` | V105 (`ROLE_ADMIN`) + **V111** (`ROLE_ATENDENTE`) | ✅ `SeedConfig` (`ROLE_ADMIN` e `ROLE_ATENDENTE`) + `DevRoleBootstrapConfig` |
| `PDV_COMANDA_COURTESY` | linha `courtesy: true` (ou `mode = TROCA`) em `POST /pdv/comandas/{id}/items` | **V115** (`ROLE_ADMIN` apenas) | ✅ `SeedConfig` (`ROLE_ADMIN`) + `DevRoleBootstrapConfig` — **não** em `ATENDENTE_PERMISSIONS` |
| `PDV_COMANDA_SURCHARGE` | `surchargeAmount > 0` em `POST /pdv/comandas/{id}/items` | **V117** (`ROLE_ADMIN` apenas) | ✅ `SeedConfig` (`ROLE_ADMIN`) + `DevRoleBootstrapConfig` — **não** em `ATENDENTE_PERMISSIONS` |
| `PDV_COMANDA_DISCOUNT` | `discountAmount > 0` em `POST /pdv/comandas/{id}/close` | **V119** (`ROLE_ADMIN` apenas) | ✅ `SeedConfig` (`ROLE_ADMIN`) + `DevRoleBootstrapConfig` — **não** em `ATENDENTE_PERMISSIONS` |

Comanda (PDV-F009) ganhou permissão **própria**, separada de `PDV_SALE_MANAGE` — é uma superfície
operacional diferente (tab de horas vs. venda pontual), e granularidade de concessão separada não
custa mais que esta linha a mais de `@PreAuthorize`. Leitura continua sob `PDV_READ`.

A V105 concedeu `PDV_COMANDA_MANAGE` **apenas ao `ROLE_ADMIN`**, e quem opera comanda no balcão é o
atendente: com `PDV_READ` da V86 ele abre `/pdv/comandas` e lista as mesas ocupadas, mas
abrir/lançar/fechar/cancelar respondia **403**. A **V111** estende a concessão ao `ROLE_ATENDENTE`.
Cancelar comanda ficou na mesma permissão, sem `PDV_COMANDA_CANCEL` separada — o estorno de estoque
do abandono já é auditado (`origin=PDV_COMANDA_CANCEL`). Na mesma correção, `SeedConfig` passou a
conceder as permissões do atendente em `dev`/`hml` (`ATENDENTE_PERMISSIONS`): a role era criada
vazia e, com o Flyway desligado em `dev`, a V86 nunca rodava — o atendente local não passava de 403
em nenhum endpoint do PDV.

**`PDV_COMANDA_COURTESY` fica só no `ROLE_ADMIN`, e isso é deliberado** (decisão do dono,
2026-08-28). Lançar linha a preço zero é um desconto de 100%, e o projeto já tratou desconto como
decisão com dono em `PDV_SALE_DISCOUNT`: dar sessão de graça — free hosh de domingo, double hosh de
quarta — é decisão da casa, não do atendente de plantão. O atendente que tentar lançar cortesia
recebe `403 COURTESY_NOT_ALLOWED`, e isso é o comportamento correto.

> ⚠️ **Não confundir com o buraco que a V111 corrigiu.** Ali (`PDV_COMANDA_MANAGE`) o atendente
> não conseguia sequer operar a mesa, o que era bug. Aqui a restrição é a regra de negócio. O
> `PROMPT_BACKEND_SESSAO_MESA.md` §1 do `frontend-admin-prod` descreve a promo como "aplicada pelo
> operador", o que já sugeriu uma vez que faltava conceder — fica registrado aqui para a próxima
> análise não redescobrir isso como lacuna de RBAC. Do lado do front, a permissão já está em
> `permissions.constants.ts`; basta esconder o controle de cortesia de quem não a tem.

**`PDV_COMANDA_DISCOUNT` é própria, e não a reutilização de `PDV_SALE_DISCOUNT`** (PDV-F014). É a
terceira permissão de mesa separada da equivalente de balcão, seguindo `PDV_COMANDA_COURTESY` e
`PDV_COMANDA_SURCHARGE`: alçada de salão e alçada de caixa são concedidas a pessoas diferentes — quem
fecha o salão à noite não é necessariamente quem opera o balcão de dia —, e granularidade separada
não custa mais que uma linha de checagem no controller.

O **teto**, esse, é compartilhado (`pdv.sale.max-discount-percent`, default 10%) e a checagem é
literalmente a mesma função (`PdvService.requireDiscountWithinLimit`). O limite é política comercial
da casa, não característica do canal, e duas chaves de configuração poderiam divergir em silêncio.

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

> ⚠️ Este parágrafo dizia, até 2026-08-28, que **não havia endpoint de abertura de sessão** e que
> o operador criava sessões à mão no banco. Isso deixou de ser verdade em **PDV-F001**
> (2026-07-28): `POST /pdv/sessions` existe, com fundo de troco, depósito e dono. A nota estava
> contradizendo a própria tabela de endpoints logo acima.

### Rate limiting

❌ Nenhum endpoint deste módulo é limitado. Ver PLAT-C030.

### Isolamento de dados

Single-tenant. O vínculo operador↔caixa **foi fechado em PDV-C004**: registrar venda e movimentar
dinheiro exigem que a sessão pertença ao operador autenticado (`403 SESSION_NOT_OWNED`), e o
depósito da venda vem da sessão em vez do request — o operador não baixa estoque de depósito alheio.

A única operação da sessão que não exige posse é o **fechamento**, deliberadamente: a conferência é
do gerente.

**A mesa é a segunda exceção, aberta em PDV-F010:** a decisão do dono é *caixa por atendente, mesas
compartilhadas*. Quem assume o posto do colega precisa ver e operar todas as mesas do salão, então
`addItem`, `closeComanda` e `cancelComanda` usam `PdvService.requireOpenSession` (sessão aberta, sem
posse) em vez de `requireOwnOpenSession`. **Abrir** comanda continua exigindo a própria sessão — a
mesa nasce numa gaveta, e é o depósito dela que baixa estoque.

Não é regressão de PDV-C004: aquele resolveu a *venda de balcão*, onde vender no caixa alheio criava
diferença sem dono, e `registerSale`/`registerCashMovement` seguem exigindo posse. O consumo da mesa
é do salão, não do operador. A contrapartida está em **onde o dinheiro entra**: o pedido nasce na
sessão de quem fecha, não na que abriu a comanda.

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

`POST /pdv/sessions` publica `CASH_SESSION_OPENED`, `POST /pdv/sessions/{id}/movements` publica
`CASH_MOVEMENT_REGISTERED` e `POST /pdv/sessions/{id}/close` publica `CASH_SESSION_CLOSED` com
esperado/contado/diferença — o ciclo de caixa é auditado ponta a ponta. Cashback creditado publica
`CASHBACK_EARNED`, na venda de balcão e no fechamento de comanda.

✅ **A comanda tem `EventType` próprio desde PDV-C014**: `COMANDA_OPENED`, `COMANDA_ITEM_ADDED`,
`COMANDA_ITEM_REMOVED`, `COMANDA_CLOSED` e `COMANDA_CANCELLED`, todos com o `comandaId` na frente do
payload — é a chave por onde se procura a mesa depois de um fechamento estranho.

Antes disso a comanda inteira andava pendurada em `STOCK_MOVEMENT_REGISTERED`, discriminada por
`origin`, e **abrir a mesa não deixava rastro nenhum**, ao contrário de abrir caixa. No lançamento e
no cancelamento o evento emprestado era ao menos fiel (o estoque de fato se move); **no fechamento
não era** — `closeComanda` não toca em saldo, e o evento entrava na trilha de movimentação de estoque
descrevendo algo que não aconteceu.

Os eventos novos **substituíram** o emprestado em vez de conviver com ele: nada no código consulta o
log de auditoria por tipo, e emitir dois eventos por lançamento dobraria o volume para manter viva
justamente a linha que mentia. **O rastro item a item continua onde sempre esteve**, em
`stock_movement` — não era ele que estava nesses eventos. Sem migration: `audit_logs.action` é
`VARCHAR(80)` livre, sem `CHECK` de valores.

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `cash_register_session`, `cash_movement` (V66), `sales_order`/`order_item` (V57, renomeadas na V65), `order_payment` (V68), `comanda`/`comanda_item` (V104) | módulo indisponível |
| `CashbackUseCase` (chamada síncrona in-process) | resolve e carimba a taxa por item na venda e no fechamento de comanda | pedido falha e reverte |
| `CrmUseCase.findCustomerNames` (chamada síncrona in-process) | resolve `customerName` em lote na listagem de mesas | listagem sem nome do cliente |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior |
| `EstoqueUseCase` (chamada síncrona in-process) | baixa de saldo por item + alerta de reposição | venda inteira falha e reverte |

Sem fila, sem impressora fiscal, sem integração de pagamento. Venda e baixa de estoque
compartilham a **mesma transação**: `InsufficientStockException` em qualquer item reverte a
venda inteira, e nada é persistido.

### Configuração

| Chave | Default | Para quê |
|---|---|---|
| `pdv.sale.max-discount-percent` | `10` | Teto do desconto, **compartilhado** entre a venda de balcão (PDV-F004) e o fechamento de mesa (PDV-F014). Acima dele, `409 DISCOUNT_LIMIT_EXCEEDED` |
| `pdv.comanda.service-fee-percent` | `10` | Taxa de serviço da mesa (PDV-F015). Zero desliga a cobrança sem mexer em código |

Ambas migram para `system_config` junto com o painel de configuração, como o resto das chaves de
política comercial do projeto.

### Limites operacionais

- `GET /pdv/sessions`: `page` ≥ 0 e `size` entre 1 e 100, via Bean Validation (`@Validated` no
  controller).
- `POST /pdv/sessions/{id}/sales`: itens obrigatórios e quantidade > 0 via `@Valid`; **sem teto
  de itens por venda**. O total é calculado no servidor (`SaleItem.subtotal()`), nunca aceito
  do cliente.
- `GET /pdv/comandas` e `GET /pdv/sessions/{id}/movements`: `page` ≥ 0 e `size` entre 1 e 100
  (default 50), via Bean Validation — as duas eram as últimas rotas do módulo sem teto, e o teto
  entrou **junto** com a abertura da primeira para a loja inteira, não depois dela (PDV-C012).
- `POST /pdv/comandas`: `tableOrCustomerLabel` tem `@Size(max = 100)`, casando com a coluna
  `VARCHAR(100)` da V104 (PDV-C011). Antes só tinha `@NotBlank`, e um rótulo mais longo atravessava
  a validação para estourar no banco como **500** em vez de **400**.

### Riscos conhecidos

- **PLAT-C035** — a garantia de "uma sessão aberta por operador" depende de um índice parcial que
  o H2 não suporta; sob concorrência, só o Postgres protege, e isso nunca foi testado.
- **PLAT-C030** — sem rate limit.
- **PDV-F013** — a baixa de estoque da comanda não é transacionalmente atômica ao longo da vida
  dela: cada `POST /pdv/comandas/{id}/items` debita e commita por conta própria (não dá para
  segurar uma transação de banco aberta pelas horas em que uma comanda fica em uso). Uma comanda
  esquecida aberta, sem `POST .../cancel` explícito, deixa estoque debitado sem devolução
  automática — não há varredura/timeout para esse caso. (PDV-F012 deu um caminho manual de
  devolução linha a linha, mas continua sendo manual.)
- **PDV-F016 / PDV-F017** — não há como transferir/juntar mesas nem dividir a conta por pessoa.

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

**V120 — `pdv_payment_cancelled_status`** (PDV-C015 — a liquidação passa a registrar pagamento)
- `ck_order_payment_status` estendido com `CANCELLED`. Só isso: o resto já existia.
- **Por que `CANCELLED` e não `FAILED`**: `FAILED` é a cobrança que o gateway recusou, e é assim
  que ela aparece em qualquer investigação de pagamento. Aqui nada falhou — a cobrança foi
  abandonada porque o dinheiro entrou por outro caminho. Reaproveitar `FAILED` faria o relatório de
  falha de gateway crescer com pagamentos que deram certo.
- **Não confundir com `REFUNDED`**: estorno é dinheiro que entrou e voltou (linha nova, ledger
  append-only). `CANCELLED` é dinheiro que nunca entrou — a linha `PENDING` é **atualizada no
  lugar**, mesma exceção documentada de `OrderPayment.confirmCaptured` e pela mesma razão: não há
  movimento de dinheiro para registrar, só o encerramento de uma cobrança em aberto.
- Molde da migration: **V79**, que estendeu o `CHECK` irmão de método para `GATEWAY_PIX`.

**V118 — `pedido_service_fee`** (PDV-F015 — a taxa de serviço da mesa)
- `sales_order.service_fee_amount NUMERIC(14,2) NOT NULL DEFAULT 0`. **`NOT NULL DEFAULT 0` sem
  backfill condicional**, ao contrário de `cost_price` na V65: nenhum pedido gravado antes desta
  entrega cobrou taxa, então zero é o valor historicamente verdadeiro — não um zero que mente.
- `CHECK`s: `ck_sales_order_service_fee_non_negative` e `ck_sales_order_service_fee_only_mesa`
  (serviço de mesa só existe onde há mesa — espelha o compact constructor de `Order`, mesma razão
  de `ck_sales_order_change_only_balcao` existir).
- **Coluna própria, deliberadamente fora de `net_amount`:** o líquido é somado como receita em
  quatro agregações (`sumConcludedNetAmountBySessionId`, `findRevenueTotals`, por canal e por dia),
  e a gorjeta é do garçom — a loja apenas a repassa. Nenhuma dessas queries precisou mudar.

**V119 — `pdv_comanda_discount_permission`** (PDV-F014)
- Seed de `PDV_COMANDA_DISCOUNT` para `ROLE_ADMIN`, no molde exato da V115 e da V117.

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

**V104 — `pdv_comanda`** (PDV-F009 — comanda de mesa)
- `comanda` (id, session_id FK → `cash_register_session`, warehouse_code,
  table_or_customer_label, status, order_id FK → `sales_order`, opened_by, opened_at, closed_at).
  Índice `idx_comanda_session_status`.
- `CHECK`s: `ck_comanda_status` (`ABERTA`/`FECHADA`/`CANCELADA`) e
  `ck_comanda_status_consistency`, que espelha o compact constructor de `Comanda` — `ABERTA` sem
  `closed_at` nem `order_id`; `FECHADA` com os dois; `CANCELADA` com `closed_at` e **nunca**
  `order_id`, porque comanda cancelada nunca virou pedido.
- `comanda_item` (id, comanda_id FK ON DELETE CASCADE, sku, quantity, unit_price, cost_price,
  product_name, added_at), `CHECK ck_comanda_item_quantity_positive`, índice
  `idx_comanda_item_comanda_id`. O item congela preço e custo no **lançamento**, não no
  fechamento — `cost_price` fica **nulo** quando o produto não tem custo conhecido, nunca um
  `DEFAULT 0` que mentiria sobre a margem.
- `comanda.warehouse_code` repete o depósito da sessão pela mesma razão de PDV-C004: não abrir,
  pela porta da comanda, a brecha de baixar estoque de depósito alheio.

**V105 — `pdv_comanda_permission`**
- Seed `PDV_COMANDA_MANAGE`, **só para `ROLE_ADMIN`** — permissão própria, separada de
  `PDV_SALE_MANAGE`: tab de horas e venda pontual são superfícies operacionais diferentes.

**V111 — `pdv_comanda_permission_atendente`** (correção da V105)
- Estende `PDV_COMANDA_MANAGE` ao `ROLE_ATENDENTE`. A V105 concedeu só ao admin, e quem opera
  comanda no balcão é o atendente: com `PDV_READ` (V86) ele listava as mesas, mas
  abrir/lançar/fechar/cancelar respondia **403**. Na mesma correção, `SeedConfig` ganhou
  `ATENDENTE_PERMISSIONS` — a role era criada vazia e, com Flyway desligado em `dev`, a V86 nunca
  rodava.

**V112 — `estoque_product_sessao_mesa`** (PDV-F010, lado do catálogo)
- `product` ganha `available_for_table` (`NOT NULL DEFAULT TRUE`), `session_product`
  (`NOT NULL DEFAULT FALSE`), `sessions_per_unit` (`CHECK ck_product_sessions_per_unit_positive`)
  e `open_rosh_price` (`CHECK ck_product_open_rosh_price_non_negative`). Todos aditivos: nenhum
  produto já cadastrado muda de comportamento.
- O `DEFAULT TRUE` de `available_for_table` é **obrigatório, não cosmético** — é o único jeito de
  todo produto já cadastrado preservar o comportamento implícito de hoje (sai na mesa). Mesma
  escolha e mesma razão de `visible_in_pos` na V91, e é ortogonal a ela: bebida e narguilé saem na
  mesa e no balcão, cigarro e isqueiro só no balcão.
- **Não existe campo `sessionPrice`:** "Essência Blueberry" e "Sessão de narguilé" são dois
  produtos distintos no catálogo, e os sabores da sessão são as **variações da grade** — o
  `sale_price` de cada variação já *é* o preço de sessão daquele sabor. Nada muda em
  `product_variant`.
- `sessions_per_unit` é **só sugestão** para o diálogo de conversão de estoque do admin — não
  movimenta saldo sozinho.
- Lata de essência e sessão de narguilé são **SKUs distintos**, com conversão explícita
  (saída de 1 lata, entrada de N sessões) pelo `POST /estoque/movements` que já existia. A
  alternativa — saldo fracionado, baixando `1/N` — mudaria o contrato de quantidade de ajuste,
  contagem e reposição de uma vez, já que todo campo de quantidade do sistema é inteiro.

**V113 — `pedido_canal_mesa`** (PDV-F010 — o canal `MESA` no pedido)

O miolo desta migration **não** é acrescentar um valor ao enum: é que a **V65 gravou quatro
invariantes de canal no schema, e três delas rejeitam um pedido de mesa**. Adicionar `MESA` sem
relaxá-las faria todo fechamento de comanda estourar `CHECK` no `INSERT` — e só em produção, porque
o teste de domínio passaria. As mesmas regras vivem duplicadas no compact constructor de `Order`
(o domínio é a primeira barreira, o schema é a que sobrevive a carga direta), e os dois lados
mudam juntos.

- **(a)** `ck_sales_order_channel` recriado com `BALCAO`/`MESA`/`MARKETPLACE`.
- **(b)** `ck_sales_order_customer_by_channel`: `MESA` entra na exceção junto com `BALCAO` — a mesa
  pode ser aberta sem vínculo de cadastro, que é o caso normal do salão. Marketplace continua
  exigindo cliente: pedido online sem cliente não tem para quem entregar nem para quem estornar.
- **(c)** `ck_sales_order_session_by_channel`: `MESA` passa a **exigir** sessão, junto com
  `BALCAO` — a comanda nasce dentro de uma sessão aberta, e pedido de mesa sem caixa não teria
  gaveta para conferir. Marketplace continua livre (pode ter sessão: pedido do app pago na loja).
- **(d)** `ck_sales_order_change_only_balcao` vira `ck_sales_order_change_amount_by_channel`: a
  mesa fecha pela **mesma** `validatePaymentsAndComputeChange` do balcão, então pagamento em
  espécie gera troco igual. Manter a regra barraria toda mesa paga em dinheiro com valor tendido
  a mais.
- **(e)** Nova `ck_sales_order_mesa_origin`: `comanda_id` e `table_label` são obrigatórios em
  `MESA` e proibidos fora dela.
- Novas colunas `comanda_id` (FK → `comanda`, índice `idx_sales_order_comanda_id`) e
  `table_label`. `sales_order.comanda_id` é **redundante com `comanda.order_id` de propósito** —
  os dois são gravados na mesma transação de `closeComanda`, e sem esta coluna listar pedidos de
  mesa com o rótulo exigiria join reverso em toda página de Vendas > Pedidos. `table_label` é
  congelado no fechamento pela mesma razão de `order_item.product_name`: renomear a mesa depois
  não pode reescrever o histórico.
- **Backfill:** os pedidos já gerados por fechamento de comanda são **reclassificados** para
  `MESA` cruzando com `comanda.order_id`. É a **única** vez que o canal — documentado como
  imutável — é reescrito; a imutabilidade vale para a aplicação, e a exceção de migration
  corrigindo classificação histórica já tinha sido aberta pela V65. Sem o backfill, o filtro
  "Mesa" não mostraria nada de antes da entrega e a análise por canal ficaria com uma quebra na
  série. A ordem importa: o `UPDATE` preenche `comanda_id`/`table_label` **junto** com o canal, ou
  a `ck_sales_order_mesa_origin` recém-criada rejeitaria a própria linha sendo corrigida.

**V114 — `pdv_comanda_sessao`** (PDV-F010 — cliente, modo e cortesia)
- `comanda.customer_id` FK → `customers` + índice. Distinto de `table_or_customer_label`, que é
  rótulo de tela e nunca foi vínculo de cadastro — é o `customer_id` que faz o pedido da mesa sair
  com nome e gerar cashback.
- `comanda_item` ganha `mode` (default `'NORMAL'`), `courtesy` (default `FALSE`) e `linked_item_id`
  (auto-FK). `CHECK`s: `ck_comanda_item_mode` (lista fechada),
  `ck_comanda_item_courtesy_is_free` (cortesia ⇒ `unit_price = 0`; **a recíproca não vale**, e é
  por isso que a coluna existe em vez de ser inferida), `ck_comanda_item_linked_by_mode`.
- O mesmo par `mode`/`courtesy` entra em `order_item`, para o histórico da mesa distinguir cortesia
  de item cobrado. **Sem `linked_item_id` no pedido, de propósito:** o vínculo entre linhas decide
  se a tela mostra "Trocar sabor" numa comanda aberta, e no pedido fechado não responde nada.

**V115 — `pdv_comanda_courtesy_permission`** (PDV-F010)
- Seed `PDV_COMANDA_COURTESY`, **só para `ROLE_ADMIN`** — ver a nota em Permissões RBAC. Distribuir
  depois é uma migration de uma linha; recolher uma permissão já distribuída é que custa caro.

**V116 — `pdv_comanda_notes_surcharge`** (PDV-F011 — o setup da mesa e o acréscimo do open rosh)
- `comanda_item` ganha `notes VARCHAR(200)` e `surcharge_amount NUMERIC(14,2)`, ambos nullable.
- Três `CHECK`, que são as mesmas regras do service gravadas no schema — a barreira que sobrevive a
  um caminho de escrita futuro que esqueça a validação:
  `ck_comanda_item_surcharge_non_negative`; `ck_comanda_item_surcharge_not_on_courtesy` (irmã de
  `ck_comanda_item_courtesy_is_free` — valor extra numa linha que o cliente não paga é
  contradição, não caso de borda); e `ck_comanda_item_surcharge_only_open_rosh`, porque nos demais
  modos a diferença do sabor caro já mora no `pricing` da variante.
- `order_item` ganha as **mesmas duas colunas**, pela razão que já obrigara `mode`/`courtesy` a
  atravessarem na V114: "qual pinça saiu com aquela mesa" é uma pergunta feita **depois** de a mesa
  fechar, e se a nota morresse na comanda a tela de Vendas > Pedidos não teria como respondê-la.
  Só `non_negative` acompanha — os outros dois `CHECK` são invariantes da comanda, não do pedido.
- **O `unit_price` gravado já inclui o acréscimo.** A coluna existe à parte para o relatório
  separar as parcelas depois, exatamente como `discount_amount` não vira "preço menor".

**V117 — `pdv_comanda_surcharge_permission`** (PDV-F011)
- Seed `PDV_COMANDA_SURCHARGE`, **só para `ROLE_ADMIN`**, mesmo raciocínio da V115 na direção
  oposta: se lançar uma linha a zero é um desconto de 100% e tem dono, subir o preço à mão também
  tem. O valor é decidido no balcão, sem tabela que o justifique depois — é o tipo de lançamento
  que precisa de um nome atrás dele quando o fechamento não bater.

**Nota de modelagem:** `sales_order`/`order_item`/`order_payment` referenciam depósito só por
texto livre (`warehouse_code`), sem FK — mesmo padrão de `stock_balance`/`stock_movement` em
`estoque` (ver EST-C002 no README daquele domínio). Nenhuma validação equivalente a
`ProductRepository.existsBySku` existe para `warehouse_code` neste módulo hoje.

## Cobertura de Testes

| Arquivo | Tipo | O que cobre |
|---|---|---|
| `CashRegisterSessionTest` | Unit (domínio) | `open`, `closedWith` (transição, divergência, double-close), invariantes de `of()`, `belongsTo` |
| `CashMovementTest` | Unit (domínio) | `register`, `signedAmount` (sinal vem do tipo), invariantes, reconstituição |
| `OrderTest` | Unit (domínio) | `openBalcao`/`openMarketplace`, todas as transições (incl. `reserved`/`pickedUp`/reembolso pós-reserva), violações de invariante, cópia defensiva de itens e (PDV-F015) `withServiceFeeOf`/`totalPayable`, com o caso que fixa a decisão: a taxa move o total a pagar e **não** o líquido |
| `DiscountProrationTest` | Unit (domínio) | PDV-F014 — o rateio do desconto de conta: proporcionalidade, soma exata ao centavo em 16 combinações, teto por linha, cortesia absorvendo zero, desconto de 100%, e as quatro recusas (negativo, maior que a conta, conta zerada, lista vazia) |
| `OrderItemTest` | Unit (domínio) | `fromCatalog` (resolução de preço/custo), derivação de margem/cashback, violações de invariante |
| `OrderStatusTest` | Unit (domínio) | Completude da tabela de transições, estados terminais, validação de caminho por canal |
| `OrderPaymentTest` | Unit (domínio) | `captured`/`pending`/`refunded`/`confirmCaptured`, invariantes |
| `PdvServiceTest` | Unit (Mockito) | Sessão, fechamento (incl. PDV-C005: recusa com mesa aberta, sem nem calcular o esperado; e PDV-C017/C018: troco e estorno saindo do esperado), movimentos, venda (posse, preço do catálogo, desconto, ordem de validação de pagamento antes do estoque, `reserveForPickup`, cálculo de troco incl. pagamento dividido, propagação de falhas sem salvar), liquidação online (PDV-C015: pagamento capturado, recusa antes de consumir a reserva, recusa de excedente e encerramento da cobrança de gateway) |
| `PdvCashCycleIT` | `@SpringBootTest` | Ciclo completo (abrir → vender → sangrar → fechar), pagamento dividido **com o troco saindo da gaveta** (PDV-C017), estorno em dinheiro reduzindo o esperado (PDV-C018), pedido do app liquidado em dinheiro entrando no esperado (PDV-C015), netting de movimentos, isolamento entre operadores, unicidade de numeração e (PDV-C013) a paginação de sessões percorrida página a página, exigindo que nenhuma se repita nem se perca |
| `PdvSaleConcurrencyIT` | `@SpringBootTest` | Vendas concorrentes nunca vendem além do saldo |
| `OrderServiceTest` | Unit (Mockito) | `changeStatus` (incl. caso especial `pickedUp`), `cancelOrder` (libera reserva), `refundOrder` (fan-out de estoque/pagamento/cashback), guardas de duplo cancelamento/reembolso |
| `OrderRefundIT` | `@SpringBootTest` | Reembolso e cancelamento fim a fim |
| `OrderRefundConcurrencyIT` | `@SpringBootTest` | Reembolso concorrente: só um sucede |
| `PdvControllerTest` | MockMvc standalone | Contrato HTTP básico dos endpoints principais |
| `PdvControllerSecurityTest` | MockMvc + Security | 401/403 por autoridade, 404s, 400s, posse de sessão, preço servidor-side (ignora preço enviado pelo cliente) |
| `PedidoRepositoryIT` | `@SpringBootTest` + `@Transactional` | Numeração de pedido, round-trip de todo campo congelado, combinações de filtro, sobrevivência de `reservedAt`/timestamps da esteira |
| `PedidoRepositoryPostgresIT` | `@SpringBootTest` (Postgres real, via Testcontainers) | Variante da IT acima nas particularidades do dialeto Postgres |
| `ComandaTest` | Unit (domínio) | `open`/`withAddedItem`/`closed`/`cancelled`, coexistência status↔`closedAt`↔`orderId`, cópia defensiva de itens e (PDV-F012) `withRemovedItem`/`itemsRemovedWith`/`chargedChildrenOf`: cascata da `TROCA`, `SABOR_EXTRA` exposto para o service barrar, remoção de folha, comanda esvaziada e as recusas |
| `ComandaItemTest` | Unit (domínio) | `fromCatalog` vs. `forSession` (preço de fora, custo do catálogo), cortesia livre para o cliente mas não para a margem, `linkedItemId` por modo, leitura de linha legada como `NORMAL` |
| `ComandaServiceTest` | Unit (Mockito) | Abertura com posse, débito imediato, ordem das validações antes do estoque, os quatro modos de consumo (incl. open rosh cobrando o preço do pai), cortesia e `TROCA` implícita, vínculo de linha, não-repreçamento no fechamento, gaveta de quem fecha, `COMANDA_ONLY_COURTESY`, carimbo da taxa de cashback, devolução no cancelamento, e (PDV-F011) acréscimo somando sobre o preço do pai, recusa em cortesia/fora de `OPEN_ROSH`/negativo, `notes` recusada acima de 200 |
| `ComandaRepositoryIT` | `@SpringBootTest` + `@Transactional` | Round-trip da comanda e dos itens com preços congelados, transições `FECHADA`/`CANCELADA`, campos de sessão e cliente, linha de cortesia com custo congelado, estabilidade dos ids de item (para `linkedItemId` sobreviver) e, desde PDV-C007/C009/C012, a listagem de mesas: loja inteira sem `sessionId`, filtro por `warehouseCode`, paginação estável entre páginas, página além do fim, a consulta não-paginada da guarda de fechamento, e a **contagem de consultas** que prova o fim do N+1 |
| `ComandaCashCycleIT` | `@SpringBootTest` | Ciclo completo contra banco real: abrir → lançar com débito verificado a cada item → fechar com pagamento dividido; devolução de todos os itens no cancelamento; mesa com cliente e duplo em cortesia gerando pedido `MESA` com cashback; e (PDV-F014/F015) o fechamento com desconto rateado **e** taxa, com round-trip da taxa no banco e a prova de que o `expectedAmount` do caixa enxerga a gorjeta paga em dinheiro |
| `PdvComandaControllerTest` | MockMvc standalone | Contrato HTTP das 5 rotas, `runningTotal`, 403 de cortesia (com e sem a flag, incluindo `TROCA`), resolução do nome do cliente no CRM (uma consulta em lote para a página inteira), o `PageResult` da listagem, a listagem da loja sem `sessionId` e o `mode` serializado como enum |
| `PdvComandaControllerSecurityTest` | MockMvc + Security | 401/403 por autoridade em cada rota (`PDV_COMANDA_MANAGE` para escrita, `PDV_READ` para leitura), os 404s, e (PDV-C011/C012) os 400 de `size` acima do teto, `page` negativa e rótulo de mesa acima de 100 caracteres — casos que só valem na cadeia real, porque o setup standalone não monta a validação de parâmetro |
| `ComandaConcurrencyIT` | `@SpringBootTest`, **sem `@Transactional`** | PDV-C008: seis lançamentos simultâneos na mesma mesa sem perder linha (um SKU por thread, para isolar da contenção de saldo); os mesmos seis no **mesmo** SKU, onde quem perde a corrida otimista do estoque toma `STOCK_UPDATE_CONFLICT` e a comanda ainda fecha a conta com o que passou; seis fechamentos simultâneos gerando **um** pedido; fechar × cancelar com exatamente um vencedor |

**Lacunas conhecidas** (registradas para não maquiar como "tudo coberto"):
1. Não existe IT de persistência **dedicado** para `CashRegisterSession`/`CashMovement` —
   a cobertura de round-trip vem só indiretamente de `PdvCashCycleIT`.
2. **PLAT-C035** — a garantia "uma sessão aberta por operador" depende do índice parcial único
   `uk_cash_register_session_open_operator`, que o H2 (perfil `dev`, `ddl-auto=create-drop`) não
   suporta. Só o Postgres real protege essa invariante sob concorrência, e esse caminho nunca foi
   exercitado por teste.
3. A contagem de consultas passou a ser aferida em **um** ponto do módulo — a listagem de mesas,
   em `ComandaRepositoryIT.findOpen_loadsItemsWithoutOneQueryPerComanda`, via `Statistics` do
   Hibernate. É o único: nenhuma outra leitura do módulo tem guarda de regressão contra N+1.
4. `PdvComandaControllerSecurityTest` **não tem caso para `PDV_COMANDA_SURCHARGE`**: o 403 de
   acréscimo é exercitado só no `PdvComandaControllerTest` standalone, que não sobe a cadeia de
   segurança. A cortesia tem os dois; o acréscimo, só um.
5. **A ordem de deleção das linhas órfãs (PDV-F012) não é verificável por teste.**
   `ComandaItemEntity.linkedItemId` é mapeado como coluna `Long` simples, não `@ManyToOne`, então o
   schema gerado por `ddl-auto` (H2, perfil `dev` das ITs) **não tem a FK auto-referente** que a
   V114 cria no Postgres. Apagar a linha pai antes da `TROCA` que aponta para ela só falharia no
   banco real. `ComandaRepositoryImpl.save` remove as órfãs em ordem decrescente de id justamente
   para que a filha saia sempre primeiro — a correção é **por construção**, e é a mesma classe de
   lacuna de PLAT-C035 (índice parcial que o H2 não suporta).

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
| PDV-F010 | 🔴 Alta | Feature | sessao-de-narguile-na-mesa-e-canal-mesa | O lounge vende a mesma essência de duas formas (R$ 14 avulsa no balcão, a partir de R$ 25 montada em narguilé na mesa) e pratica duplo, "pague 1 leve 2", free hosh de domingo, double hosh de quarta e open rosh — nada disso cabia numa comanda que era lista plana de `{sku, quantity}`. Pedido formal do `frontend-admin-prod` em `Docs/PROMPT_BACKEND_SESSAO_MESA.md`, com o front já escrito atrás de `SESSAO_ENABLED`/`MESA_ENABLED`. | ✅ Fechado (2026-08-27) — 12/12 do checklist do front. Ver Histórico abaixo. |
| PDV-C005 | 🔴 Alta | Correção | bloquear-fechamento-de-caixa-com-mesa-aberta | `PdvService.closeSession` (`core/service/PdvService.java:119`) fecha o caixa **sem consultar as comandas abertas dele**, mas `ComandaService.addItem` (`:112`) e `cancelComanda` (`:269`) exigem a sessão de origem aberta via `requireOpenSession`. Fechado o turno com mesa aberta, as duas passam a responder `409 CASH_REGISTER_SESSION_CLOSED` **para sempre**: a mesa congela e **nunca mais pode ser cancelada**, deixando o estoque já debitado item a item sem nenhum caminho de devolução. A única saída vira `closeComanda` — cobrar de um cliente que talvez já tenha ido embora. Hoje a regra existe **só no cliente** (`frontend-admin-prod` commit `48ee8bc`, 26/08/2026, "bloqueia fechamento de caixa com mesas abertas"); o servidor aceita. | ✅ Fechado (2026-08-29) — ver Histórico abaixo. |
| PDV-C006 | 🟡 Importante | Correção | documentar-pdv-f010-no-readme-e-registry | PDV-F010 foi implementado e commitado sem chegar à documentação: este README ficou congelado em PDV-F009 e `docs/feature-registry.md` também. Faltavam endpoints, `PDV_COMANDA_COURTESY`, as regras de preço por modo, as migrations V104/V111–V115 e as 7 classes de teste de comanda. | ✅ Fechado (2026-08-28) — feito nesta própria `/1-analise`; ver Histórico. |
| PDV-C007 | 🟡 Importante | Correção | listar-comandas-abertas-da-loja-sem-sessionid | `PdvComandaController.listOpenComandas` (`:179`) exige `@RequestParam Long sessionId`, e `ComandaJpaRepository` só tem `findBySessionIdAndStatusOrderByIdDesc`. Como a decisão é "caixa por atendente, **mesas compartilhadas**", o front tem que buscar `GET /pdv/sessions`, filtrar as `OPEN` e disparar **uma chamada por sessão** (`pdv.service.ts::listComandasAbertas`, com `Promise.allSettled` para uma sessão que falhe não derrubar o salão). Proposta do §6 do `PROMPT_BACKEND_SESSAO_MESA.md`: `GET /pdv/comandas` sem `sessionId` (ou `?warehouseCode=`), devolvendo as mesas abertas da **loja** — troca o merge N+1 por uma chamada só. | ✅ Fechado (2026-08-30) — `sessionId` virou opcional em vez de rota nova; ver Histórico abaixo |
| PDV-F011 | 🔴 Alta | Feature | componentes-da-sessao-e-acrescimo-no-open-rosh | **Pedido aberto do front, 0/8.** `notes` (string, máx. 200, nullable, sem efeito em preço) e `surchargeAmount` (decimal, `>= 0`, somado ao preço resolvido pelo servidor) em `AddComandaItemRequest`, ecoados em `ComandaItemResponseDTO` **e** `OrderItemAdminResponseDTO`. `notes` dá casa ao registro do setup da mesa — qual narguilé, com filtro, **qual pinça** — que não pode virar cortesia (cortesia baixa estoque, exige permissão e apareceria no cupom como item de R$ 0 não pedido). `surchargeAmount` é a essência que sai mais cara mesmo no open rosh, decidida caso a caso no balcão; só vale em `mode = OPEN_ROSH`, soma sobre o `openRoshPrice` do **produto pai**, e **não é `discountAmount` negativo** — o relatório precisa distinguir "cobramos a mais" de "cobramos a menos". Exige `PDV_COMANDA_SURCHARGE` própria, no espírito de `PDV_COMANDA_COURTESY`; `costPrice` segue congelado (o acréscimo é margem, não custo). Erros: `403 SURCHARGE_NOT_ALLOWED`, `400 SURCHARGE_ON_COURTESY`, `400 SURCHARGE_NOT_APPLICABLE`, `400 SURCHARGE_INVALID`, `400 NOTES_TOO_LONG`. Spec colável em `frontend-admin-prod/Docs/PROMPT_BACKEND_COMPONENTES_SESSAO.md`; front pronto atrás de `REGISTRO_COMPONENTES_ENABLED`/`ACRESCIMO_OPEN_ROSH_ENABLED`, com um teste-guarda que **falha de propósito** quando o contrato chegar. | ✅ Fechado (2026-08-29) — 8/8 do checklist do front. Ver Histórico abaixo. |
| PDV-F012 | 🟡 Média | Feature | remover-item-de-comanda-aberta | Não existe endpoint de remover linha de comanda: lançamento errado numa mesa só sai cancelando a comanda **inteira**, que devolve tudo ao estoque e encerra a mesa. A regra difícil já está antecipada no §8.2 do `PROMPT_BACKEND_SESSAO_MESA.md` — remover uma linha `OPEN_ROSH` tem que arrastar as `TROCA` penduradas nela (`comanda_item.linked_item_id`) — e a devolução ao estoque é a mesma `ENTRADA` por item que `cancelComanda` já faz. | ✅ Fechado (2026-08-30) — `DELETE /pdv/comandas/{id}/items/{itemId}`; a `TROCA` é arrastada, o `SABOR_EXTRA` barra. Ver Histórico abaixo |
| PDV-F013 | 🟢 Baixa | Feature | varredura-de-comanda-esquecida | A baixa de estoque da comanda não é atômica ao longo da vida dela (cada `addItem` é seu próprio commit — não dá para segurar transação de banco aberta por horas), então comanda esquecida aberta sem `POST .../cancel` explícito deixa estoque debitado sem devolução. Não há varredura nem timeout. Era limitação documentada em prosa desde PDV-F009; ganha ID para poder entrar em sprint. Molde possível: `StockReservationExpiryCleanupService` / `CashbackExpiryCleanupService`. | Pendente |
| PDV-C008 | 🔴 Alta | Correção | comanda-sem-trava-de-concorrencia | `ComandaEntity` (`adapter/out/persistence/entity/ComandaEntity.java`) **não tem `@Version`**, e `ComandaRepositoryImpl.save` (`:45-88`) é um read-modify-write do agregado inteiro: `findById`, reescreve todos os campos e regrava a coleção de itens. É a exceção no projeto — `OrderEntity`, `StockBalanceEntity` e `StockLotEntity` **têm** `@Version`. Falha concreta: dois atendentes lançam item na mesma mesa quase ao mesmo tempo, que é justamente o que PDV-F010 liberou ao trocar `requireOwnOpenSession` por `requireOpenSession` (`core/service/PdvService.java:330`). As duas transações leem a mesma versão da comanda e a segunda gravação sobrescreve a primeira — **uma linha desaparece**. Só que `ComandaService.addItem` (`:139`) já chamou `adjustStock(SAIDA)` em commit próprio: a essência saiu do estoque, o cliente não é cobrado por ela, e o saldo fica furado sem nenhum rastro na comanda. Existe `PdvSaleConcurrencyIT` para a venda de balcão; não existe equivalente para comanda. | ✅ Fechado (2026-08-29) — resolvido por trava **pessimista**, não `@Version`; ver Histórico abaixo. |
| PDV-C009 | 🟡 Importante | Correção | n-mais-1-ao-listar-comandas-abertas | `ComandaEntity.items` é `fetch = FetchType.LAZY` (`:61`), `ComandaJpaRepository` tem um único método derivado sem `@EntityGraph` nem `JOIN FETCH` (`:10`), e `ComandaRepositoryImpl.findOpenBySessionId` (`:37-41`) mapeia cada comanda com `toDomain`, que toca `e.getItems()` (`:96`) — **uma consulta por mesa aberta**, além da consulta da lista. É problema distinto de PDV-C007: aquele é o N+1 **de HTTP** no cliente (uma chamada por sessão). Depois de PDV-C007 entregue o N+1 **de banco** continua, e fica pior: a chamada única traria as mesas de todas as sessões da loja de uma vez. Molde de correção: o padrão ID-first + `JOIN FETCH` de [`persistence.md`](../../persistence.md), o mesmo que PED-C002 rastreia para `GET /orders`. | ✅ Fechado (2026-08-30) — ID-first + `JOIN FETCH`, junto com PDV-C007; ver Histórico abaixo |
| PDV-C010 | 🟡 Importante | Correção | tipar-mode-como-enum-nos-dtos-de-resposta | `ComandaItemResponseDTO.mode` (`:31`) e `OrderItemAdminResponseDTO.mode` (`:44`) são `String`, embora `AddComandaItemRequest.mode` já seja o enum `ConsumptionMode`. O contrato sai assimétrico: o OpenAPI publica enum no request e `string` solta na resposta. Pedido explícito do front (`frontend-admin-prod/Docs/BACKEND_TODO.md:413-417`) — por causa disso o `ModoItemComanda` continua mantido à mão em `sessao-mesa.models.ts` em vez de sair do client gerado. `ConsumptionMode` vive em `core/domain/model/pedido/` e o adapter já o importa no request: é trocar o tipo nos dois DTOs e no `ComandaDTOConverter`. | ✅ Fechado (2026-08-30) — `ConsumptionMode` nos dois DTOs de resposta |
| PDV-C011 | 🟡 Importante | Correção | validar-tamanho-do-rotulo-da-mesa | `OpenComandaRequest.tableOrCustomerLabel` tem `@NotBlank` e **nenhum `@Size`**, mas a coluna é `comanda.table_or_customer_label VARCHAR(100) NOT NULL` ([`V104__pdv_comanda.sql:14`](../../../src/main/resources/db/migration/V104__pdv_comanda.sql)). Rótulo acima de 100 caracteres atravessa a validação e estoura no banco como `DataIntegrityViolationException` → **500**, quando o correto é **400**. O molde certo está no próprio módulo: `CashMovementRequest.reason` usa `@Size(max = 255)` casando com a coluna. Na mesma correção entra `@Validated` na classe `PdvComandaController`, hoje ausente ao contrário de `PdvController` (`:63-68`). | ✅ Fechado (2026-08-30) — `@Size(max = 100)` + o `@Validated` que PDV-C012 exigia na mesma classe |
| PDV-C012 | 🟢 Melhoria | Correção | listagens-do-pdv-sem-teto-de-paginacao | Duas rotas devolvem `List` inteira, sem `page`/`size`: `GET /pdv/comandas` (`PdvComandaController:177-182`) e `GET /pdv/sessions/{id}/movements` (`PdvController:159-164`). Contrasta com todo o resto do módulo, onde as leituras têm `@Min(0)`/`@Max(100)` (`/pdv/sessions`, `/pdv/sessions/{id}/sales`, `/pdv/pending-online-orders`). Hoje o tamanho é contido pelo salão e pelo turno, mas **PDV-C007 vai abrir a primeira para a loja inteira** — o teto precisa entrar junto com aquela mudança, não depois dela. | ✅ Fechado (2026-08-30) — as duas rotas paginadas, na mesma mudança que abriu a primeira para a loja |
| PDV-C013 | 🟢 Melhoria | Correção | paginacao-de-sessoes-sem-ordenacao | `CashRegisterRepositoryImpl.findAll` (`:26-30`) usa `PageRequest.of(page, size)` **sem `Sort`**. Paginação sem `ORDER BY` não tem ordem determinística: o Postgres pode devolver a mesma sessão em duas páginas e omitir outra. A rota afetada é `GET /pdv/sessions`. O resto do módulo é explícito na ordem (`findBySessionIdAndStatusOrderByIdDesc`, "mais recentes primeiro" em `/pdv/sessions/{id}/sales`). | ✅ Fechado (2026-08-30) — `Sort` por `id DESC` em `findAll` |
| PDV-C014 | 🟢 Melhoria | Correção | auditoria-da-comanda-com-eventtype-emprestado | `openComanda` (`PdvComandaController:118-127`) **não publica evento nenhum** — abrir mesa não deixa rastro, enquanto abrir caixa publica `CASH_SESSION_OPENED`. E `closeComanda` (`:209-213`) publica `STOCK_MOVEMENT_REGISTERED` com `origin: PDV_COMANDA_CLOSE`, embora **nenhum estoque se mova no fechamento** — o próprio service diz isso em `ComandaService:251`. A trilha de movimentação de estoque acaba descrevendo algo que não aconteceu. Raiz: não existe `COMANDA_OPENED`/`COMANDA_CLOSED`/`COMANDA_CANCELLED` em `AuditEvent.EventType`, e a comanda inteira anda pendurada em `STOCK_MOVEMENT_REGISTERED` + `origin`. | ✅ Fechado (2026-08-30) — cinco `EventType` próprios, substituindo o `STOCK_MOVEMENT_REGISTERED` emprestado |
| PDV-F014 | 🟡 Média | Feature | desconto-no-fechamento-de-comanda | `ComandaService.closeComanda` (`:239`) grava `BigDecimal.ZERO` fixo no `discountAmount` de cada `OrderItem`, com o comentário *"Sem desconto por item nesta entrega (fora de escopo do PDV-F009)"*. A venda de balcão tem desconto desde PDV-F004: campo em `SaleRequest`, permissão `PDV_SALE_DISCOUNT`, teto `pdv.sale.max-discount-percent` e `409 DISCOUNT_LIMIT_EXCEEDED`. Na mesa o desconto de fim de noite não tem onde ir: ou se lança linha de cortesia (que baixa estoque e é outra coisa), ou se cobra fora do sistema. Reaproveita `PdvService.requireDiscountWithinLimit` (`:339-350`), que hoje simplesmente não é chamado no fechamento de comanda. Não confundir com PDV-F011: aquele é **acréscimo por linha** em `OPEN_ROSH`, este é **abatimento na conta**. | ✅ Fechado (2026-08-30) — rateado entre os itens, não solto no pedido; ver Histórico abaixo |
| PDV-F015 | 🟡 Média | Feature | taxa-de-servico-na-comanda | Os 10% do garçom são o padrão do salão e **não existem em lugar nenhum do sistema** (uma varredura por `service_fee`, `serviceFee`, "taxa de servi", `couvert` e `gorjeta` volta vazia em `src/`). `Order.netAmount = grossAmount − discountAmount − cashbackRedeemed` (`core/domain/model/pedido/Order.java:116-121`) não tem campo de acréscimo — o único acréscimo desenhado é o `surchargeAmount` de PDV-F011, que é por linha e só vale em `OPEN_ROSH`. Hoje a taxa é somada de cabeça e cobrada por fora, o que significa que ela **não entra no fechamento de caixa, não aparece no comprovante e não é conferível**. Desenho natural: campo opcional no `CloseComandaRequest` (o cliente pode recusar), percentual configurável no molde de `pdv.sale.max-discount-percent`. | ✅ Fechado (2026-08-30) — em coluna própria, **fora** do `netAmount`; ver Histórico abaixo |
| PDV-F016 | 🟢 Baixa | Feature | transferir-e-juntar-comandas | `tableOrCustomerLabel` é imutável: `Comanda` só expõe `withAddedItem`, `closed` e `cancelled`, e não há `PATCH`/`PUT` em `/pdv/comandas`. Trocar de mesa hoje só é possível cancelando (o que devolve tudo ao estoque e encerra a comanda) e relançando item a item. Juntar duas mesas que viraram uma conta só também não tem caminho. Depende de PDV-C008: mover linhas entre agregados sem trava de concorrência multiplica o problema de perda de item. | Pendente |
| PDV-C015 | 🔴 Alta | Correção | liquidacao-online-nao-registra-pagamento | `PdvService.settleOnlineOrder` (`core/service/PdvService.java:299`) consome a reserva e conclui o pedido, mas **nunca grava `OrderPayment`** — o único caminho de recebimento do projeto fora do ledger (`registerSale` e `closeComanda` gravam). Três consequências: (1) `closeSession` calcula o esperado com `sumCapturedAmountBySessionIdAndMethod(..., DINHEIRO)`, então o pedido do app pago em dinheiro no balcão soma **zero** ali — a cédula está na gaveta, a conferência não a espera, e o fechamento acusa **sobra sem dono**; (2) `/payment-totals`, `GET /pdv/sales/{id}` e o comprovante saem sem o pagamento; (3) a linha `PENDING`/`GATEWAY_PIX` que `ShopService.checkout:213` grava em todo pedido de marketplace fica **órfã para sempre**, descrevendo uma cobrança de gateway que não vai acontecer. Mesma família de PDV-F015: dinheiro que entra na loja e não é conferível. | ✅ Fechado (2026-08-30) — a rota ganhou corpo; ver Histórico abaixo |
| PDV-C017 | 🔴 Alta | Correção | troco-nao-descontado-do-esperado-do-caixa | `order_payment.amount` em `DINHEIRO` é o valor **entregue** pelo cliente — `SalePaymentRequest` diz isso explicitamente ("pode passar do total da venda e virar troco"), `validatePaymentsAndComputeChange` deriva o troco de `total pago − líquido`, e o front manda o recebido inteiro (`pdv-pagamento.component.spec.ts:131`, *"manda o valor recebido inteiro e calcula o troco"*). Mas `PdvService.closeSession:155-157` somava `sum(CAPTURED, DINHEIRO)` **sem subtrair `sales_order.change_amount`**. Toda venda em dinheiro com troco inflava o esperado exatamente pelo troco, e o operador honesto fechava o turno acusando uma **falta** igual à soma dos trocos do dia — em toda venda quebrada, todo dia. O teste `PdvCashCycleIT.splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer` **fixava o defeito como correto**: afirmava esperado 60,00 numa gaveta que só podia conter 57,00. | ✅ Fechado (2026-08-30) — ver Histórico abaixo |
| PDV-C018 | 🟡 Importante | Correção | estorno-nao-reduz-o-esperado-do-caixa | `OrderService.refundOrder` grava `OrderPayment.refunded(payment)` — linha nova `REFUNDED`, ledger append-only — e **deixa a `CAPTURED` original de pé**, que é o desenho certo para o histórico. Só que `sumCapturedAmountBySessionIdAndMethod` filtra `p.status = 'CAPTURED'`: estornar uma venda em dinheiro no mesmo turno devolve a cédula ao cliente e o esperado continua contando-a. Achado junto com PDV-C017, e agravado por um teste vazio: `PdvCashCycleIT.cancelledSaleDoesNotCountTowardsTheExpectedAmount` **não cancelava nada** (abria caixa, vendia, fechava — com um comentário interno dizendo "sem venda cancelada"), e o README citava esse teste como prova da regra. | ✅ Fechado (2026-08-30) — subtração própria + o teste refeito como estorno de verdade; ver Histórico abaixo |
| PDV-C016 | 🟢 Melhoria | Correção | descontos-impossiveis-sem-codigo-de-erro-proprio | Dois descontos aritmeticamente impossíveis respondiam com o **400 genérico** do handler de `IllegalArgumentException` (`BAD_REQUEST`, mensagem fixa "Requisição inválida", que descarta a do domínio) — indistinguível de qualquer corpo malformado, num módulo cujo vocabulário de erro é específico em todo o resto. (1) `POST /pdv/sessions/{id}/sales` com `items[].discountAmount` maior que `quantity × unitPrice`, barrado pelo compact constructor de `OrderItem`. (2) `POST /pdv/comandas/{id}/close` com `discountAmount` maior que o total da conta, barrado por `DiscountProration.distribute` — e este é o pior dos dois **por causa da ordem**: o rateio roda antes de `requireDiscountWithinLimit`, então pedir 11% de desconto devolvia o `409 DISCOUNT_LIMIT_EXCEEDED` que a tela trata, e pedir o dobro da conta devolvia "Requisição inválida". `CloseComandaRequest.discountAmount` só tem `@DecimalMin("0.0")`, e teto não é expressável em Bean Validation porque depende da conta. | ✅ Fechado (2026-08-30) — exceção tipada com 409 próprio nos dois caminhos; ver Histórico |
| PDV-F017 | 🟢 Baixa | Feature | dividir-conta-por-pessoa | `closeComanda` gera **um único** `Order` com todos os itens, e não há campo de pessoa em `ComandaItem`. O split que existe é de **forma de pagamento** (`payments` como lista, PDV-F006) — que é outra coisa: divide *como* se paga, não *quem* paga o quê. "Cada um paga o que consumiu" não tem representação no modelo, e é o pedido mais comum de mesa cheia depois da própria comanda. | Pendente |

> A permissão `PDV_SALE_MANAGE` está ausente dos seeders de dev — rastreado como
> **EST-C001** em [`estoque`](../estoque/README.md#backlog-do-módulo), porque o sintoma
> aparece no fluxo de baixa de estoque.

> `GET /estoque/movements` exige `ESTOQUE_STOCK_MANAGE` — permissão de escrita — para uma leitura
> que o PDV usa no diálogo de conversão de estoque. Rastreado como **EST-C015** em
> [`estoque`](../estoque/README.md#backlog-do-módulo), que é o dono do endpoint.

> As sobrecargas de conveniência de `ComandaUseCase` (`addItem`/`openComanda` com menos
> argumentos) são `default` da interface e **perdem a transação** quando chamadas — o proxy JDK
> executa o default no target e a delegação vira self-invocation. Nenhum controller usa, mas um
> teste usou e fez a trava de PDV-C008 parecer quebrada. Rastreado como **PLAT-C047** em
> [`plataforma`](../plataforma/README.md#backlog-do-módulo).

> O handler de `IllegalArgumentException` (`GlobalExceptionHandler:725`) responde 400 com mensagem
> **fixa**, descartando a do domínio. É uma escolha defensável — as mensagens dos records citam
> valores e nomes de campo interno —, mas faz toda invariante alcançável por payload ler igual para
> o cliente. PDV-C016 resolveu os dois casos deste módulo tipando-os; o padrão geral está registrado
> como **PLAT-C048** em [`plataforma`](../plataforma/README.md#backlog-do-módulo).

> ⚠️ **Achado colateral de PDV-C017, roteado a `pedido`:** `OrderService.refundOrder` estorna
> `original.amount()`, que em `DINHEIRO` é o valor **entregue** pelo cliente — não o retido. Numa
> venda de R$22 paga com R$25 e R$3 de troco, o estorno devolve R$25, R$3 a mais do que a loja
> recebeu. A fórmula do fechamento corrigida aqui é fiel ao que o código faz (por isso fecha), mas
> o valor estornado em si continua errado. Não foi corrigido nesta fatia: é do domínio `pedido` e
> mexe em quanto dinheiro volta para o cliente, o que é decisão do dono.

> ✅ A documentação do domínio `pedido` foi posta em dia em **2026-08-30** (PED-C003): o README de
> lá agora descreve o canal `MESA`, `comandaId`/`tableLabel`, a taxa de serviço e os quatro campos
> que a mesa acrescentou ao `order_item` — mais as oito migrations que faltavam na tabela de schema.
> Ver [`pedido`](../pedido/README.md#modelo-de-domínio).

## Histórico de Implementações

- **2026-08-30** — `descontos-impossiveis-ganham-codigo-proprio` (**PDV-C016**): dois descontos
  aritmeticamente impossíveis respondiam com o 400 genérico do handler de
  `IllegalArgumentException` — `BAD_REQUEST`, mensagem fixa "Requisição inválida", a mesma de
  qualquer corpo malformado. Num módulo em que todo o resto tem código próprio
  (`PRODUCT_NOT_PRICED`, `DISCOUNT_LIMIT_EXCEEDED`, `SURCHARGE_ON_COURTESY`…), eram os dois pontos
  em que o servidor sabia exatamente o que estava errado e não contava.

  **No balcão**, `items[].discountAmount` acima do bruto da linha. A recusa tipada foi para
  `OrderItem.fromCatalog` e não para o compact constructor: `fromCatalog` é o caminho de
  **entrada**, onde o valor veio do cliente HTTP; o constructor é reconstituição, onde o mesmo
  valor seria dado corrompido ou erro de programação. As duas checagens coexistem de propósito, e
  há teste para cada uma.

  **Na mesa**, `discountAmount` acima do total da conta — o pior dos dois, e não pelo código em si
  mas **pela ordem**: `DiscountProration` roda antes de `requireDiscountWithinLimit`, então pedir
  11% de desconto devolvia o `409 DISCOUNT_LIMIT_EXCEEDED` que a tela trata, e pedir o dobro da
  conta devolvia "Requisição inválida". O erro mais grosseiro dava a pior resposta. A checagem
  ficou no service e **não** dentro de `distribute`: aquela é função pura de aritmética, e o
  vocabulário de erro do PDV não é dela. Sem migration.

  > **Correção de rota registrada:** a análise que abriu este item afirmava que os dois caminhos
  > respondiam **500**, por não existir `@ExceptionHandler(IllegalArgumentException.class)`. Ele
  > existe desde antes, em `GlobalExceptionHandler:725`, e responde 400. O item continua válido —
  > 400 genérico não é o código que a tela precisa, e a inversão de ordem na mesa é real —, mas a
  > severidade caiu de 🟡 para 🟢: nunca houve erro de servidor aqui.

- **2026-08-30** — `esperado-do-caixa-conta-o-que-sai` (**PDV-C015 + PDV-C017 + PDV-C018**): os
  três buracos da mesma fórmula, fechados juntos porque são a mesma linha de código sendo
  corrigida três vezes.

  **O defeito comum:** `expectedAmount` somava entradas e nada mais. `expected = abertura +
  DINHEIRO capturado + movimentos` descreve uma gaveta em que dinheiro só entra — e as duas saídas
  em espécie do PDV (troco e estorno) não passam por sangria, então não apareciam em lugar nenhum.

  **PDV-C017 — o troco.** `order_payment.amount` em `DINHEIRO` é o valor *entregue*: é o que
  `SalePaymentRequest` documenta, é de onde `validatePaymentsAndComputeChange` deriva o troco, e é
  o que o front manda. A cédula do troco volta para a mão do cliente na mesma hora, e nada a
  subtraía. Toda venda quebrada em dinheiro inflava o esperado, e o turno fechava acusando
  **falta** — o operador honesto aparecendo como devedor pela soma dos trocos do dia. Corrigido com
  `OrderRepository.sumChangeAmountBySessionId`, **sem filtro de status** de propósito: o troco saiu
  na venda e não volta, nem quando o pedido é depois reembolsado.

  **PDV-C018 — o estorno.** O ledger é append-only por desenho, e está certo assim: `refundOrder`
  grava uma linha `REFUNDED` nova e preserva a `CAPTURED`. A consequência é que somar capturados
  descreve tudo que entrou e nada do que voltou. Corrigido com uma consulta própria
  (`sumRefundedAmountBySessionIdAndMethod`) em vez de afrouxar o filtro da existente — a soma de
  capturados continua significando exatamente o que significava.

  **PDV-C015 — a liquidação online.** `settleOnlineOrder` era o único caminho de recebimento do
  projeto que não gravava linha de pagamento: concluía o pedido e pronto. O dinheiro entrava na
  gaveta e o ledger não sabia, então o mesmo fechamento acusava **sobra** sem dono, e o valor não
  aparecia em `/payment-totals` nem no comprovante. A rota ganhou corpo (`payments`, mesmo shape da
  venda de balcão), o pagamento é validado **antes** de consumir a reserva, e a cobrança
  `PENDING`/`GATEWAY_PIX` que o checkout deixa aberta é **encerrada** como `CANCELLED` (V120) em
  vez de ficar pendurada para sempre. **Valor exato, sem troco:** o canal permanece `MARKETPLACE`,
  onde `Order` não admite `changeAmount` — aceitar o excedente sem ter onde gravá-lo seria
  reintroduzir PDV-C017 por outra porta, e `400 CHANGE_NOT_SUPPORTED` é a recusa.

  **Dois testes estavam errados, e um deles fixava o bug.**
  `PdvCashCycleIT.splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer` afirmava esperado
  60,00 num caixa que abriu com 50, recebeu 10 e devolveu 3 — 57,00 é o único valor que a gaveta
  podia conter. E `cancelledSaleDoesNotCountTowardsTheExpectedAmount` **não cancelava nada**: abria
  caixa, vendia, fechava, com um comentário interno admitindo "sem venda cancelada". A regra que o
  README dizia estar coberta por ele nunca foi exercitada — e além de não coberta, estava errada.
  Virou `refundedSaleTakesTheCashBackOutOfTheExpectedAmount`, que estorna de verdade. (Estorno e
  não cancelamento porque é o que a máquina de estados permite: venda de balcão nasce `CONCLUIDO`
  com pagamento capturado, e estado pós-pagamento só alcança `REEMBOLSADO`.)

  Efeito colateral desejado em `ecommerce`: um webhook atrasado para pedido já liquidado no balcão
  vira no-op **sem chamada externa** — `PaymentWebhookService.findPendingPayment` não acha mais
  `PENDING` e retorna antes de reconsultar o gateway.

  Migration V120 (`CANCELLED` no `ck_order_payment_status`, molde da V79). Quebra de contrato em
  `POST .../settle`, com consumidor real — ver a nota acima da tabela de regras.

- **2026-08-30** — `higiene-da-comanda` (**PDV-F012 + PDV-C013 + PDV-C014**): o que restou do
  backlog de mesa depois do dinheiro estar resolvido.
  **F012 — remover linha sem derrubar a mesa.** Até aqui, lançamento errado numa comanda só saía
  cancelando a comanda **inteira**: devolve tudo ao estoque, encerra a mesa e obriga a relançar item
  a item um consumo que continua acontecendo. `DELETE /pdv/comandas/{id}/items/{itemId}` devolve o
  estoque da linha (`ENTRADA`, mesmo padrão do cancelamento) e deixa a mesa aberta.
  **A decisão que não era óbvia foi a cascata.** `linked_item_id` é FK auto-referente, então uma
  linha com filhas não pode simplesmente sumir — mas as duas espécies de filha são coisas
  diferentes. A `TROCA` é cortesia, de valor zero, e não existe sem o consumo livre que a originou:
  é arrastada junto, como o §8.2 do prompt do front já previa. O `SABOR_EXTRA` é linha **própria e
  possivelmente cobrada** — o segundo sabor de um duplo sem promo —, e arrastá-lo tiraria dinheiro
  da conta sem ninguém pedir: ele **barra** a remoção com `409 LINKED_ITEM_IS_CHARGED`, listando os
  ids para o operador removê-los antes. "Removi o narguilé errado" não é o mesmo pedido que "tira o
  segundo sabor da conta". A recusa vem **antes** de tocar o estoque, porque cada devolução é seu
  próprio efeito e abortar no meio deixaria saldo devolvido sem a linha ter saído.
  Sem permissão nova, e isso segue o precedente do próprio módulo: `cancelComanda` ficou sem
  `PDV_COMANDA_CANCEL` própria porque o estorno já é auditado — e cancelar a mesa inteira é
  estritamente mais destrutivo do que remover uma linha dela. Exigir mais para o menos seria
  incoerente. Quarto caminho de mutação, portanto quarta leitura travada (PDV-C008).
  **C014 — a comanda saiu do `EventType` emprestado.** Abrir mesa não deixava rastro nenhum, ao
  contrário de abrir caixa; e os três eventos que a comanda publicava eram todos
  `STOCK_MOVEMENT_REGISTERED` discriminados por `origin`. No lançamento e no cancelamento isso era
  ao menos fiel; **no fechamento não era** — `closeComanda` não toca em saldo, e o evento entrava na
  trilha de movimentação de estoque descrevendo algo que não aconteceu. Agora são cinco tipos
  próprios (`COMANDA_OPENED`/`ITEM_ADDED`/`ITEM_REMOVED`/`CLOSED`/`CANCELLED`), **substituindo** o
  emprestado em vez de conviver com ele: nada no código consulta o log por tipo, e emitir dois
  eventos por lançamento dobraria o volume para manter viva justamente a linha que mentia. O rastro
  item a item continua em `stock_movement`, que não mudou. Sem migration — `audit_logs.action` é
  `VARCHAR(80)` livre.
  **C013 — paginação de sessões sem ordem.** `CashRegisterRepositoryImpl.findAll` usava
  `PageRequest.of(page, size)` sem `Sort`, e sem `ORDER BY` o banco não garante nada: a mesma sessão
  podia sair em duas páginas enquanto outra sumia, e o cliente nunca saberia. Uma linha de correção,
  e um teste que percorre todas as páginas exigindo que cada sessão apareça exatamente uma vez — a
  propriedade que importa, e que não depende de quantas sessões a base tem. Ordena por `id DESC`;
  desempate é dispensável porque a chave é única, ao contrário do ledger de EST-C012.
  Sem migration em nenhum dos três.

- **2026-08-30** — `desconto-e-taxa-de-servico-no-fechamento-de-mesa` (**PDV-F014 + PDV-F015**): os
  dois pontos em que o dinheiro cobrado na mesa não era conferível. Um é abatimento, o outro é
  acréscimo, e foram juntos porque incidem no mesmo fechamento e a **ordem entre eles importa**.
  **F014 — o desconto é rateado, não solto.** O operador pede sobre a conta ("tira 20 reais"), mas
  `Order.discountAmount` sempre foi *derivado* da soma dos descontos por item, e é sobre o líquido
  de **cada item** que o cashback é creditado e a margem calculada. Um campo de desconto no nível do
  pedido teria sido bem mais simples de escrever e faria a casa pagar cashback sobre dinheiro que
  não recebeu, além de mostrar margem cheia numa venda abatida. Daí `DiscountProration`, função pura
  e à parte: a razão de ela existir isolada é a aritmética de arredondamento, em que "quase certo"
  significa centavo sumindo da conta do cliente. **A primeira versão dela tinha um bug**, encontrado
  ao escrever o teste: a sobra do truncamento ia inteira para a maior linha, e numa conta de
  0,01 + 0,01 + 99,98 com 99,99 de desconto a maior linha já está no teto — o centavo a empurraria
  acima do próprio valor, violando a invariante de `OrderItem`. A sobra passou a ser distribuída por
  **folga**, centavo a centavo. O teto é o mesmo do balcão, e é literalmente a mesma função
  (`requireDiscountWithinLimit`); a permissão, não — `PDV_COMANDA_DISCOUNT` é a terceira permissão
  de mesa separada da de balcão, pela razão de sempre neste módulo.
  **F015 — a taxa fica FORA do `netAmount`, e essa é a decisão da entrega.** O caminho óbvio seria
  `netAmount = gross − desconto − cashback + taxa`, uma conta só. Mas `netAmount` é somado como
  **receita** em quatro agregações, e os 10% são do garçom: a loja apenas os repassa. Dentro do
  líquido, eles inflariam receita e margem, e toda agregação futura teria que lembrar de subtraí-los
  — o tipo de armadilha que se paga por anos. Então a taxa entra em coluna própria, o líquido
  continua significando "o que a loja vendeu", e o que o cliente paga vira `totalPayable()` =
  líquido + taxa, que é contra o que o pagamento e o troco são validados. **Nenhuma das quatro
  queries de receita precisou mudar**, e a conferência da gaveta também não: `closeSession` soma
  `order_payment`, não `netAmount`, então o dinheiro da taxa já era contado corretamente — há um IT
  provando exatamente isso, porque é a parte da decisão que mais fácil quebraria em silêncio.
  A taxa incide sobre o líquido, portanto **depois** do desconto: cobrar serviço sobre um abatimento
  recém-concedido seria devolvê-lo com a outra mão. E vem **aplicada por padrão** — omitir
  `applyServiceFee` significa sim —, porque é o padrão do salão e depender de o atendente lembrar de
  marcar é exatamente o problema que a feature existe para resolver; `false` é o cliente recusando.
  Novo `GET /pdv/comandas/service-fee` para a tela mostrar o valor **antes** de fechar, já que sem
  ele a única forma de descobrir quanto seria cobrado seria fechar a conta.
  **Custo colateral:** `Order` ganhou um componente, o que tocou as 14 construções internas do
  record e o mapeamento de persistência. As fábricas `of` antigas continuam valendo e delegam com
  zero — pedido anterior a esta entrega não cobrou taxa, então zero é o valor historicamente
  verdadeiro, diferente do `cost_price` da V65, em que um zero mentiria. Migrations V118/V119.

- **2026-08-30** — `superficie-de-listagem-de-mesas` (**PDV-C007 + C009 + C010 + C011 + C012**): os
  quatro itens que o README já apontava como o bloco seguinte, mais um de carona, porque todos caem
  na mesma superfície — separá-los seria três passadas no mesmo controller e no mesmo repositório.
  **C007** tirou a obrigatoriedade de `sessionId` em `GET /pdv/comandas`: a decisão do dono é *caixa
  por atendente, mesas compartilhadas*, e o servidor obrigava o cliente a listar as sessões, filtrar
  as `OPEN` e disparar **uma chamada por sessão** para remontar o salão no navegador. Escolhido
  **tornar o parâmetro opcional** em vez de criar rota nova: duas rotas fazendo quase a mesma coisa
  envelheceriam mal, e a antiga ficaria sem teto para sempre. **Não há filtro por status da sessão de
  caixa, e isso é deliberado** — desde PDV-C005 o caixa não fecha com mesa aberta, então comanda
  `ABERTA` já implica sessão `OPEN`; um join ali repetiria uma invariante que o módulo já garante.
  **C009** era o outro N+1, o de banco, e é o que tornava C007 perigoso: `items` é `LAZY` e
  `toDomain` tocava a coleção, uma consulta por mesa — a chamada única da loja inteira só deixaria
  isso pior. Resolvido com o ID-first + `JOIN FETCH` de [`persistence.md`](../../persistence.md), o
  mesmo par de `StockCountJpaRepository`. **C012** entrou na mesma mudança, não depois: uma listagem
  sem teto é contida pelo salão enquanto é de um caixa, e deixa de ser no instante em que passa a ser
  da loja. Entrou também em `GET /pdv/sessions/{id}/movements`, a outra rota sem teto — ali **sem**
  ID-first, porque `CashMovementEntity` não tem coleção filha e o bug de `LIMIT` junto de `JOIN FETCH`
  não existe. As duas rotas passaram a devolver `PageResult`: é quebra de contrato com consumidor
  real, assumida, no precedente de EST-C005. **C011** veio junto porque `@Min`/`@Max` em parâmetro de
  query não valem nada sem `@Validated` na classe, e `PdvComandaController` era o único controller de
  negócio sem ele — com o `@Validated` no lugar, o `@Size(max = 100)` do rótulo era uma linha, e
  troca um 500 do driver por um 400 honesto. **C010** tipou `mode` como `ConsumptionMode` nos dois
  DTOs de resposta: o OpenAPI publicava enum no request e `string` solta na resposta, e era por isso
  que o cliente mantinha `ModoItemComanda` à mão em vez de gerá-lo.
  **Um efeito colateral que não estava no plano:** a guarda de PDV-C005 em `PdvService.closeSession`
  usava a mesma consulta da listagem, e paginá-la teria criado um bug silencioso — um caixa com mais
  mesas que o tamanho da página voltaria a fechar com mesa aberta. Ganhou consulta **própria e não
  paginada** (`findOpenIdsBySessionId`), que de quebra projeta só o id: a guarda carregava agregados
  inteiros para jogar fora os itens, e era mais um chamador pagando o N+1 de C009.
  **De brinde, a primeira aferição de contagem de consultas do módulo:** a lacuna nº 3 da Cobertura
  de Testes dizia que o N+1 não tinha como ser pego por regressão. `ComandaRepositoryIT` agora liga
  o `Statistics` do Hibernate e prova que quadruplicar as mesas **não muda** o número de consultas —
  a única afirmação honesta possível, porque os itens estariam acessíveis nos dois desenhos (a
  leitura acontece dentro da transação). Sem migration e sem permissão nova.

- **2026-08-29** — `componentes-da-sessao-e-acrescimo-no-open-rosh` (**PDV-F011**): os dois campos
  que não cabiam em `{sku, quantity, mode, courtesy, linkedItemId}`. **`notes`** (máx. 200, sem
  efeito nenhum em preço) dá casa ao setup da mesa — qual narguilé, com filtro, **qual pinça**. A
  alternativa seria lançar a pinça como linha de cortesia, e ela é errada por três motivos de uma
  vez: cortesia **baixa estoque** (a pinça não é consumida), exige `PDV_COMANDA_COURTESY` e sairia
  no cupom do cliente como um item de R$ 0 que ele não pediu — registro não é venda a zero.
  **`surchargeAmount`** é a essência que sai mais cara mesmo dentro do consumo livre, decidida no
  balcão caso a caso; **não é `discountAmount` negativo**, porque o relatório precisa distinguir
  "cobramos a mais" de "cobramos a menos". O `unit_price` gravado **já inclui** o acréscimo (senão
  o subtotal não fecha) e a coluna existe à parte para o relatório separar as parcelas depois —
  mesma razão pela qual `discount_amount` não vira "preço menor" em `order_item`. O acréscimo soma
  sobre o `openRoshPrice` do produto **pai**, nunca sobre o preço da variação do sabor: é a mesma
  armadilha de PDV-F010, e é por isso que a linha com acréscimo deixa de usar `fromCatalog` e passa
  por `forSession` (novo `plainCatalogLine` em `ComandaService`) — `fromCatalog` resolveria o preço
  pelo SKU e não teria onde guardar os dois campos. `costPrice` segue congelado normalmente: o
  acréscimo é **margem, não custo**. Ordem de validação que importa: cortesia é checada **antes**
  do modo, por causa de `TROCA`, que é cortesia por definição — invertida, uma `TROCA` com
  acréscimo responderia `SURCHARGE_NOT_APPLICABLE` em vez do `SURCHARGE_ON_COURTESY` que descreve
  o erro real. `notes` acima de 200 **recusa, não trunca** (`NOTES_TOO_LONG`): truncar em silêncio
  perderia justamente o fim da anotação, que é onde costuma estar a pinça. Permissão nova
  `PDV_COMANDA_SURCHARGE`, **só `ROLE_ADMIN`** como a `COURTESY` — e ela só é exigida para
  acréscimo **positivo**: zero é no-op e negativo é `400 SURCHARGE_INVALID`, não `403`, porque
  pedido malformado não é pedido não autorizado. Migrations V116 (as duas colunas em
  `comanda_item` **e** `order_item`, com três `CHECK` que codificam as mesmas regras do service) e
  V117 (permissão). Pedido formal do `frontend-admin-prod`
  (`Docs/PROMPT_BACKEND_COMPONENTES_SESSAO.md`), entregue **8/8**.
- **2026-08-29** — `comanda-sem-trava-de-concorrencia` (**PDV-C008**): PDV-F010 liberou dois
  atendentes na mesma mesa e a comanda era o **único agregado mutável do módulo sem trava**.
  `ComandaRepositoryImpl.save` é um read-modify-write do agregado inteiro, então duas gravações
  concorrentes faziam a segunda apagar a linha da primeira — com o estoque **já debitado** em
  commit próprio por `addItem`: a essência saiu, o cliente não foi cobrado, e o saldo ficou furado
  sem nenhum rastro na comanda. **Por que a trava é pessimista e não `@Version`**, que é o que
  `OrderEntity`/`StockBalanceEntity`/`StockLotEntity` usam: a versão otimista só colide quando o
  UPDATE chega a ser emitido, e o Hibernate compara o agregado com o snapshot que ele mesmo
  carregou — regravar um estado velho por cima não conta como alteração, nenhum UPDATE sai e
  nenhuma colisão é detectada. A corrida aqui é sobre a **leitura** que fundamenta a decisão
  (`requireOpen`), e é lá que a trava tem que estar. Novo `ComandaRepository.findByIdForUpdate`
  (`@Lock(PESSIMISTIC_WRITE)`, molde de `RefreshTokenJpaRepository.findByTokenHashForUpdate`),
  usado pelos **três** caminhos que mudam a comanda — `addItem`, `closeComanda`, `cancelComanda`.
  `getComanda`, que é a consulta de tela, fica **de fora** de propósito: quem só desenha o salão
  não decide nada, e travar a linha a cada refresh seguraria a mesa contra quem quer lançar nela.
  Trava só o cabeçalho, não a coleção de itens: a corrida que interessa é sobre o **status**, e
  mesas diferentes seguem em paralelo. Sem migration. Coberto por `ComandaConcurrencyIT` (sem
  `@Transactional` na classe — uma transação de teste envolvendo tudo faria as threads
  compartilharem contexto e o teste passaria por engano). `concurrentAddItem_neverLosesALine` usa
  **um SKU por thread**, para que a única contenção seja a da comanda; o irmão
  `..._sameSku_conflictsInsteadOfLosingALine` põe as seis no mesmo SKU e afirma **coerência**
  em vez de contagem — linhas, total e saldo todos iguais ao número de lançamentos que
  passaram —, porque ali entra também a trava **otimista** do `stock_balance`, que é outro
  agregado. **Armadilha que este teste custou caro para descobrir:** ele chamava a sobrecarga
  de conveniência `addItem(id, sku, qty, user)`, que é `default` de `ComandaUseCase`. Através
  do proxy JDK o default roda no target e a delegação para o método real vira
  self-invocation, então o `@Transactional` **não é aplicado**: o `SELECT FOR UPDATE`
  commitava e soltava a trava na própria chamada, e o teste produzia um lost update que o
  caminho de produção não tem — o controller sempre usou a assinatura completa. Rastreado
  como **PLAT-C047** em [`plataforma`](../plataforma/README.md#backlog-do-módulo); o teste
  passou a chamar o mesmo caminho que o controller.
- **2026-08-29** — `bloquear-fechamento-de-caixa-com-mesa-aberta` (**PDV-C005**): `closeSession`
  fechava o caixa sem olhar as comandas daquela sessão, enquanto `addItem` e `cancelComanda`
  exigem a sessão de origem aberta. O resultado era uma mesa **congelada para sempre**: as duas
  passavam a responder `409 CASH_REGISTER_SESSION_CLOSED`, a comanda nunca mais podia ser
  cancelada, e o estoque debitado item a item ficava sem caminho de devolução — a única saída era
  `closeComanda`, cobrando um cliente que talvez já tivesse ido embora. Agora `closeSession`
  consulta `comandaRepository.findOpenBySessionId` e responde `409 SESSION_HAS_OPEN_COMANDAS`.
  **É a única regra do ciclo de caixa que bloqueia em vez de apenas registrar** — divergência de
  contagem não bloqueia, e isso é deliberado desde PDV-F002; mesa aberta bloqueia porque é uma
  porta que ainda dá para fechar agora e não dará mais depois. A checagem vem **antes** de
  calcular o `expectedAmount`: recusar é a decisão, e computar a conferência de um fechamento que
  não vai acontecer é trabalho jogado fora. `PdvService` passou a receber o **port**
  `ComandaRepository`, não o `ComandaService` — este service já é dependência daquele desde
  PDV-F009, e inverter a seta criaria ciclo de beans. Sem migration. A regra existia só no cliente
  (`frontend-admin-prod` commit `48ee8bc`, 26/08); agora existe no servidor.

- **2026-08-28** — `documentar-pdv-f010-no-readme-e-registry` (**PDV-C006**): PDV-F010 estava no
  código desde 27/08 e não estava em lugar nenhum da documentação — este README parava em PDV-F009
  e `docs/feature-registry.md` também. Auditoria pura, sem mudança de código: entraram os campos e
  os seis códigos de erro novos na tabela de endpoints, a permissão `PDV_COMANDA_COURTESY` no RBAC,
  um bloco de 21 regras "Comanda de mesa" em Regras de Negócio (cada uma com o teste que a
  exercita), as migrations V104 e V111–V115 no Schema e as 7 classes de teste de comanda em
  Cobertura. De passagem, três coisas que a análise achou **erradas** no doc, não só ausentes: a
  tabela de Estrutura Hexagonal ainda listava `Sale`/`SaleItem` e `CashRegisterSession (stub)`,
  substituídos em PDV-F003/F004/F005; a seção de Segurança afirmava que **não havia endpoint de
  abertura de sessão**, contradizendo a tabela de endpoints logo acima (PDV-F001 fechou isso em
  2026-07-28); e a linha de Infraestrutura citava `cash_register_sale`/`sale_item`, renomeadas na
  V65. Registrada também a **decisão do dono de 2026-08-28** sobre `PDV_COMANDA_COURTESY` continuar
  só no `ROLE_ADMIN` — cortesia é do gerente —, para a próxima análise não redescobrir a concessão
  restrita como lacuna de RBAC. Na mesma passagem entraram os seis itens novos de backlog, entre
  eles **PDV-C005** (🔴) e **PDV-F011** (o pedido aberto do front).
- **2026-08-27** — `sessao-de-narguile-na-mesa-e-canal-mesa` (**PDV-F010**): a comanda deixou de ser
  lista plana de `{sku, quantity}`. Pedido formal do `frontend-admin-prod`
  (`Docs/PROMPT_BACKEND_SESSAO_MESA.md`), entregue **12/12** com o front consumindo no mesmo dia.
  Cinco decisões estruturais:
  **(1) `ConsumptionMode`** (`NORMAL`/`OPEN_ROSH`/`SABOR_EXTRA`/`TROCA`) é **ortogonal a
  `courtesy`**: o modo diz *o que* a linha é, a cortesia diz se foi cobrada. `SABOR_EXTRA` pode ser
  cobrado (duplo sem promo) ou não (promo "pague 1 leve 2"); `TROCA` é cortesia por definição, e o
  servidor não depende do cliente marcar o campo — troca cobrada seria narguilé vendido duas vezes
  na mesma sessão de valor fixo. Inferir um do outro pelo preço zero mentiria: um desconto de 100%
  dá o mesmo zero.
  **(2) A armadilha do open rosh.** A linha chega com o SKU da **variação** do sabor (para saber
  qual essência sai do estoque), mas o valor cobrado é o `openRoshPrice` do produto **pai**.
  Resolver o preço pelo SKU, como em todos os outros modos, cobraria o preço do sabor. É o único
  modo em que o preço não sai do SKU da linha, e é por isso que `ComandaItem.forSession` recebe o
  `unitPrice` já resolvido em vez de derivá-lo do `pricing` como `fromCatalog` faz.
  **(3) Cortesia congela `costPrice` normalmente.** `unitPrice`/`netAmount` vão a zero, mas o custo
  entra igual — é o que faz a margem mostrar o prejuízo real da promo e responder a pergunta de
  negócio por trás da feature ("o open rosh está dando lucro?"). Custo nulo ali mentiria, e
  `forSession` recusa produto sem preço **mesmo em cortesia** justamente por isso.
  **(4) Canal `MESA`, imutável.** O pedido da mesa **nasce** `MESA` (`Order.openMesa`), não vira
  depois. O trabalho real ficou na V113: a V65 tinha gravado quatro invariantes de canal no schema
  e três rejeitavam pedido de mesa — sem relaxá-las, todo fechamento estouraria `CHECK` no
  `INSERT`, e **só em produção**, porque o teste de domínio passaria.
  **(5) Caixa por atendente, mesas compartilhadas.** `addItem`/`close`/`cancel` trocaram
  `requireOwnOpenSession` pelo novo `PdvService.requireOpenSession` — o consumo da mesa é do salão,
  não do operador. Não é regressão de PDV-C004, que resolveu a *venda de balcão*: `registerSale` e
  os movimentos de caixa seguem exigindo posse. **Abrir** comanda também continua exigindo a
  própria sessão, porque a mesa nasce numa gaveta e é o depósito dela que baixa estoque. A
  contrapartida decidida com o dono: quando B fecha a mesa de A, o pedido entra na gaveta de **B** —
  o dinheiro pertence a quem o recebeu, e isso impede o pedido de cair numa sessão que A já
  encerrou.
  Também entraram: `customerId` na comanda (é ele que faz o pedido da mesa sair com nome e gerar
  cashback, distinto do `tableOrCustomerLabel`, que sempre foi rótulo de tela); a taxa de cashback
  resolvida e **carimbada** no fechamento, como no balcão; `COMANDA_ONLY_COURTESY`, irmã de
  `COMANDA_EMPTY` — comanda só de cortesias não fecha, porque pelo desenho a cortesia é sempre
  acessória de uma sessão paga e total zero ali é erro de lançamento; e a regra
  `availableForTable`, que faz "bebida e narguilé saem na mesa, cigarro não" existir **no
  servidor** e não só no cliente. Permissão nova `PDV_COMANDA_COURTESY`, concedida **só ao
  `ROLE_ADMIN`** de propósito (ver Permissões RBAC). Migrations V112 (catálogo), V113 (canal, com o
  backfill que reclassifica o histórico), V114 (cliente, modo e cortesia, em `comanda_item` **e**
  `order_item`) e V115 (permissão). Cobertura: `ComandaServiceTest` ganhou 15 casos novos,
  `ComandaItemTest` 4, mais `ComandaRepositoryIT`, `ComandaCashCycleIT` e
  `PdvComandaControllerTest`. **Não implementado nesta entrega, e é o próximo pedido do front:**
  `notes` e `surchargeAmount` — rastreados como **PDV-F011**.
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

- [x] **PDV-F003 + PDV-F004 + PDV-F005** — Fatia 0: fundação do pedido. Vinha antes de tudo,
      inclusive do ciclo de caixa, porque era a única mudança cujo custo cresce com o volume de
      vendas gravadas. Fechado em 2026-07-28.
- [x] **PDV-F001 + PDV-F002 + PDV-C004** — Fatia 1: ciclo de caixa. Fechado em 2026-07-28.
- [x] **PDV-F006** — Fatia 3: pagamento com múltiplas formas e troco. Fechado em 2026-07-29.
- [x] **PDV-C001** — auditar o código e completar este README. Fechado em 2026-08-18.

Fora do roteiro do plano original, o que a mesa trouxe:

- [x] **PDV-F009** — comanda de mesa. Fechado em 2026-08-18.
- [x] **PDV-F010** — sessão de narguilé na mesa e canal `MESA`. Fechado em 2026-08-27.
- [x] **PDV-C006** — documentar PDV-F010 aqui e no registry. Fechado em 2026-08-28.
- [x] **PDV-C005** — 🔴 bloquear fechamento de caixa com mesa aberta. Fechado em 2026-08-29.
- [x] **PDV-F011** — `notes` e `surchargeAmount`, o pedido do front. Fechado em 2026-08-29.
- [x] **PDV-C008** — 🔴 trava de concorrência da comanda. Fechado em 2026-08-29, junto com os dois
      acima: foi a mesma rodada que abriu a mesa a dois atendentes de verdade.
- [x] **PDV-C007 + C012 + C009 + C010** — a superfície de listagem de mesas, os quatro juntos, mais
      **PDV-C011** de carona (a mesma classe precisava de `@Validated`). Fechado em 2026-08-30.
- [x] **PDV-F014 + F015** — desconto no fechamento e taxa de serviço. Fechado em 2026-08-30.
- [x] **PDV-F012 + C013 + C014** — remover item de comanda, ordenação na paginação de sessões e
      `EventType` próprio de comanda. Fechado em 2026-08-30.

- [x] **PDV-C016** — os dois descontos impossíveis ganharam código de erro próprio. Fechado em
      2026-08-30.
- [x] **PDV-C015 + PDV-C017 + PDV-C018** — o esperado do fechamento passou a contar o que **sai**
      da gaveta (troco e estorno), e a liquidação de pedido online passou a registrar o pagamento
      recebido. Fechado em 2026-08-30. Os três foram achados varrendo o código depois que o
      backlog documentado já estava limpo de correções — a fórmula do `expectedAmount` era o ponto
      cego: cada entrega anterior acrescentou uma forma de dinheiro **entrar** (PDV-F006 o cartão,
      PDV-F015 a taxa, PDV-F008 a reserva) e nenhuma olhou para as duas formas de ele **sair**.

**O backlog do módulo está limpo de correções.** O que resta são três features, todas 🟢 baixa, e
nenhuma delas bloqueia operação:

- **PDV-F013** — varredura de comanda esquecida. Molde: `StockReservationExpiryCleanupService`.
- **PDV-F016** — transferir e juntar mesas. Dependia de PDV-C008, que está fechado.
- **PDV-F017** — dividir a conta por pessoa. É o pedido mais comum de mesa cheia depois da própria
  comanda, e o único que exige modelo novo (`ComandaItem` não tem campo de pessoa).
