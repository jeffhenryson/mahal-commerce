# Domínio: pedido

**Status:** 🟢 Operacional — visão do administrador entregue; o pedido nasce em `vendas-balcao` (balcão) e em `ecommerce` (marketplace, checkout + webhook InfinitePay, Fatia 10, entregue em 2026-08-03)
**Pacote Java:** `com.cernecommerce.core.domain.model.pedido`
**Rota HTTP base:** `/orders`
**Última atualização deste doc:** 2026-08-03 (`Order.paid(...)` ganhou seu primeiro caller: o webhook de pagamento do marketplace, ECM-F004)

## Objetivo

O **documento de venda**, comum a todos os canais, e a superfície pelo qual o administrador o
consulta e o gerencia.

Existe um `Order` só, discriminado por `SalesChannel`, porque tudo que consome venda consome
"vendas, independente de canal": o extrato do cliente, o ledger de cashback, a devolução, o
faturamento, o documento fiscal e o relatório de margem. Duas tabelas fariam cada um desses
consumidores pagar um `UNION` ou duplicar lógica — e nenhuma interface em Java ajuda um `SELECT`.
Ver [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.1.

## Modelo de Domínio

| Tipo | Papel |
|---|---|
| `Order` | Cabeçalho: canal, status, cliente, sessão de caixa, depósito, totais e carimbos de tempo |
| `OrderItem` | Item com **três valores congelados**: `unitPrice`, `costPrice` e `cashbackPercent` |
| `SalesChannel` | `BALCAO` \| `MARKETPLACE` — a **origem**, imutável |
| `OrderStatus` | Máquina de estados, com as transições declaradas no próprio enum |

### `channel` é origem; `sessionId` é liquidação

São dimensões independentes, e confundi-las custaria caro. `channel` diz **onde o pedido nasceu** e
nunca muda. `sessionId` diz **qual caixa o liquidou**.

O caso que separa os dois: o cliente monta o pedido no aplicativo, vem à loja e paga no balcão. Esse
pedido continua `MARKETPLACE` — foi o site que o gerou, e é assim que ele tem que aparecer no
relatório de conversão — mas o dinheiro entrou numa gaveta específica, e o fechamento daquele caixa
precisa contabilizá-lo.

### Por que três valores são congelados no item

- `unitPrice` — sem ele, mudar o preço amanhã reescreveria o faturamento de ontem.
- `costPrice` — **o mais caro de retrofitar.** Sem ele, a próxima compra que alterar o custo do
  produto reescreve a margem histórica de **todos** os pedidos passados, e não há como reconstruir:
  o custo antigo não fica em lugar nenhum. Como o cashback sai da margem, isso não é detalhe
  contábil.
- `cashbackPercent` — sem ele, mudar a taxa amanhã reescreveria o valor gerado por pedidos de ontem.

Os três são **anuláveis** apenas nos pedidos anteriores à V65. Um default zero mentiria sobre a
margem; nulo diz a verdade, que é "não se sabe".

### Numeração

`order_number` vem de sequência própria (`order_number_seq`) e é emitido na **conclusão**, não na
criação: o `BIGSERIAL` do id deixa buracos quando uma transação faz rollback, e buraco em numeração
de documento fiscal é problema com o fisco. Pedidos anteriores à V65 têm prefixo `LEG-`.

## Estados e transições

```
   CRIADO ──────────────────────────────────────────► CONCLUIDO ──┐
      │  (balcão: nasce e termina na mesma transação)      │      │
      │                                                    │      │
  AGUARDANDO_PAGAMENTO ──┬── pagamento ──► PAGO ──► SEPARADO ──► ENVIADO ──► ENTREGUE
      │                  │                                                     │
      │                  └── retirada e pagamento no balcão ───────────────────┤
      ▼                                                                        ▼
  CANCELADO (só pré-pagamento)                              REEMBOLSADO (só pós-pagamento)
```

`CANCELADO` e `REEMBOLSADO` são os dois estados **terminais de verdade**, e são mutuamente
exclusivos por construção: estados pré-pagamento (`CRIADO`, `AGUARDANDO_PAGAMENTO`) só aceitam
`CANCELADO`; estados pós-pagamento (`PAGO`, `SEPARADO`, `ENVIADO`, `ENTREGUE`, `CONCLUIDO`) só
aceitam `REEMBOLSADO` — cancelar depois de pago devolveria mercadoria sem estornar o dinheiro já
recebido. `Order.refunded(reason, refundedAt)` garante essa invariante no próprio construtor
compacto.

`AGUARDANDO_PAGAMENTO → CONCLUIDO` é a retirada no balcão: terminou exatamente como uma venda de
balcão termina. Mandá-lo por `SEPARADO`/`ENVIADO` descreveria uma separação e um envio que não
aconteceram.

### `AGUARDANDO_PAGAMENTO → PAGO`: quem chama `Order.paid(...)`

Dois caminhos levam a `PAGO`, e nenhum dos dois é o cliente afirmando "eu paguei":

- **Liquidação no balcão** (`PdvService.settleOnlineOrder`) — o operador confirma o recebimento
  presencialmente.
- **Webhook do gateway** (`PaymentWebhookService.handleNotification`, ECM-F004/Fatia 10) — o
  InfinitePay notifica, e o service **reconsulta o gateway** (`PaymentGatewayPort.checkPayment`)
  antes de confiar em qualquer coisa; só chama `.paid(...)` depois de `OrderPayment` já estar
  `CAPTURED` e o valor pago bater com `netAmount()`. Ver
  [`ecommerce/README.md`](../ecommerce/README.md#gateway-de-pagamento-infinitepay--webhook-ecm-f004-fatia-10--entregue-2026-08-03)
  para o desenho completo do webhook — este README só documenta o efeito sobre o pedido.

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/orders` | `ORDER_READ` | Filtros por `channel`, `status`, `customerId`, `from`, `to`; paginado |
| `GET` | `/orders/{id}` | `ORDER_READ` | Detalhe **com custo e margem** |
| `POST` | `/orders/{id}/status` | `ORDER_FULFILL` | `SEPARADO`/`ENVIADO`/`ENTREGUE` |
| `POST` | `/orders/{id}/cancel` | `ORDER_CANCEL` | Cancela (só pré-pagamento) e **devolve a mercadoria ao estoque** |
| `POST` | `/orders/{id}/refund` | `ORDER_REFUND` | Reembolsa (só pós-pagamento): devolve estoque (com suporte a lote via `itemLots`), estorna cada pagamento `CAPTURED` com uma linha `REFUNDED` nova e reverte o cashback ganho — tudo em uma transação |

Detalhes em [`docs/api-reference.md`](../../api-reference.md#pedidos-visão-do-administrador--orders).

### Quatro permissões, não uma

As consequências são muito diferentes: ler é inócuo, avançar estágio é operação de expedição,
cancelar **mexe no estoque**, e reembolsar mexe em estoque **+ pagamento + cashback** de um pedido
já pago. Uma permissão única obrigaria a conceder o reembolso para quem só precisa despachar
pedido — ou pior, para quem só cancela pedidos pré-pagamento.

### Custo e margem só aparecem aqui

O DTO do PDV omite os dois de propósito: `PDV_READ` é a permissão mais distribuída daquele módulo, e
o operador de caixa não precisa ver quanto a loja ganha por item.

`marginAmount` do pedido é **nulo, não parcial**, quando algum item não tem custo congelado. Somar
só os itens conhecidos produziria um número que *parece* a margem do pedido e não é — pior do que
não ter número.

## Integrações entre domínios

| Domínio | Relação |
|---|---|
| `vendas-balcao` | Cria o pedido de canal `BALCAO` e o conclui na mesma transação |
| `estoque` | O cancelamento e o reembolso devolvem mercadoria com `adjustStock(ENTRADA)` (reembolso com suporte a lote); a liquidação de pedido online consome reserva |
| `ecommerce` | Cria o pedido de canal `MARKETPLACE` no checkout (`Order.openMarketplace(...)`, Fatia 9) e o leva a `PAGO` via webhook do gateway (`PaymentWebhookService`, Fatia 10) |
| `crm` | `customerId` alimenta o extrato do cliente (`CRM-F001`); o reembolso reverte o cashback ganho via `cashbackUseCase.reverseEarningsForOrder` |
| `financeiro` | Consome o pedido para DRE e provisão de cashback (`FIN-F001`) |

## Schema de Banco (Migrations)

| Migration | O que faz |
|---|---|
| V57 | Criou `cash_register_sale` e `sale_item` |
| **V65** | Renomeia para `sales_order` / `order_item`, acrescenta canal, status, numeração, cliente, desconto, cashback resgatado, troco, carimbos e `@Version`; cria `order_number_seq` |
| **V67** | Permissões `ORDER_READ`, `ORDER_FULFILL`, `ORDER_CANCEL` |
| **V71** | `pedido_reembolso` — coluna `refunded_at` e expansão do `CHECK` de status para aceitar `REEMBOLSADO`, com constraint garantindo `(status = REEMBOLSADO) == (refunded_at IS NOT NULL)` |
| **V72** | Permissão `ORDER_REFUND`, concedida a `ROLE_ADMIN` |

## Testes no Postman

Coleção: [`pedido.postman_collection.json`](pedido.postman_collection.json).

```bash
npx newman run docs/dominios/pedido/pedido.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**Pré-requisito:** rode antes a coleção de [`vendas-balcao`](../vendas-balcao/README.md) para existir
ao menos um pedido.

> ⚠️ A pasta `03` **cancela um pedido de verdade** e devolve os itens ao estoque. Rode em base de
> desenvolvimento.

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| PED-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Este README foi escrito junto com a entrega, não a partir de auditoria do código. Faltam Regras de Negócio e Cobertura de Testes no padrão de [`estoque`](../estoque/README.md). | Pendente |
| PED-F001 | 🟢 Baixa | Feature | filtro-por-numero-do-pedido | `GET /orders?orderNumber=` — hoje só dá para achar um pedido pelo id interno, e o número é o que o cliente tem em mãos. | Pendente |

`PDV-F007` (status `REEMBOLSADO`, distinto de `CANCELADO`) foi entregue em 2026-07-29 — ver
[Histórico de Implementações](#histórico-de-implementações) abaixo e o registro em
[`vendas-balcao`](../vendas-balcao/README.md).

## Histórico de Implementações

- **2026-07-28** — `fundacao-do-pedido` (PDV-F003/F004/F005): `Order`/`OrderItem` substituem
  `Sale`/`SaleItem`; V65.
- **2026-07-28** — `orders-visao-do-administrador`: `OrderUseCase`/`OrderService`,
  `OrdersController`, quatro endpoints, V67. Coberto por `OrderServiceTest` e
  `OrdersControllerSecurityTest`.
- **2026-07-29** — `cancelamento-e-reembolso-do-pedido` (`PDV-F007`, Fatia 5): `OrderStatus` ganha
  `REEMBOLSADO`, mutuamente exclusivo de `CANCELADO` por transição; `POST /orders/{id}/refund`
  (`ORDER_REFUND`, V72) devolve estoque (com suporte a lote), estorna cada pagamento `CAPTURED`
  com uma linha `REFUNDED` nova e reverte o cashback ganho, tudo em uma transação; V71 adiciona
  `refunded_at` e o `CHECK` de consistência do status. Coberto por `OrderServiceTest`,
  `OrderTest`, `OrderStatusTest` e o IT de ponta a ponta `OrderRefundIT`.
- **2026-08-03** — gateway InfinitePay + webhook (`ECM-F004`, Fatia 10, detalhado em
  [`ecommerce/README.md`](../ecommerce/README.md)): `Order.paid(...)` ganha seu primeiro caller
  fora de teste — `PaymentWebhookService`, chamado por `POST /webhooks/payments/{provider}`.
  Nenhuma mudança de schema neste domínio; V79 (em `ecommerce`) só amplia o `CHECK` de
  `order_payment.method` para aceitar `GATEWAY_PIX`.

## Próximos passos

- [ ] **PED-C001** — auditar o código e completar este README.
- [ ] Nenhum teste de concorrência para reembolso (duas chamadas simultâneas ao mesmo pedido) —
      gap silencioso, ainda sem item de backlog próprio.
- [x] Criação de pedido `MARKETPLACE`: entregue (Fatias 8-10, `ECM-F001`-`F004`). `checkout` cria
      via `Order.openMarketplace(...)`; o webhook do gateway leva a `PAGO` via `Order.paid(...)`.
- [ ] Gap conhecido, não deste domínio: se um pedido `MARKETPLACE` é cancelado com um link de
      checkout ainda pendente no InfinitePay, ninguém avisa o gateway — ver "Conhecido, fora desta
      entrega" em [`ecommerce/README.md`](../ecommerce/README.md).
