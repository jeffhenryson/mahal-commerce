# Domínio: pedido

**Status:** 🟢 Operacional — visão do administrador entregue; o pedido em si nasce em `vendas-balcao` (balcão) e nascerá em `ecommerce` (marketplace)
**Pacote Java:** `com.cernecommerce.core.domain.model.pedido`
**Rota HTTP base:** `/orders`
**Última atualização deste doc:** 2026-07-28 (criação, Fatia 1)

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
   CRIADO ──────────────────────────────────────────► CONCLUIDO
      │  (balcão: nasce e termina na mesma transação)      │
      │                                                    │
  AGUARDANDO_PAGAMENTO ──┬── pagamento ──► PAGO ──► SEPARADO ──► ENVIADO ──► ENTREGUE
      │                  │                                                        │
      │                  └── retirada e pagamento no balcão ──► CONCLUIDO         │
      ▼                                                                           ▼
                              CANCELADO ◄─────────────────────────────────────────┘
```

`CANCELADO` é o único estado **terminal de verdade**. `ENTREGUE` e `CONCLUIDO` ainda aceitam
cancelamento, porque devolução existe e precisa de um caminho.

`AGUARDANDO_PAGAMENTO → CONCLUIDO` é a retirada no balcão: terminou exatamente como uma venda de
balcão termina. Mandá-lo por `SEPARADO`/`ENVIADO` descreveria uma separação e um envio que não
aconteceram.

## API — Endpoints

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET` | `/orders` | `ORDER_READ` | Filtros por `channel`, `status`, `customerId`, `from`, `to`; paginado |
| `GET` | `/orders/{id}` | `ORDER_READ` | Detalhe **com custo e margem** |
| `POST` | `/orders/{id}/status` | `ORDER_FULFILL` | `SEPARADO`/`ENVIADO`/`ENTREGUE` |
| `POST` | `/orders/{id}/cancel` | `ORDER_CANCEL` | Cancela e **devolve a mercadoria ao estoque** |

Detalhes em [`docs/api-reference.md`](../../api-reference.md#pedidos-visão-do-administrador--orders).

### Três permissões, não uma

As consequências são muito diferentes: ler é inócuo, avançar estágio é operação de expedição, e
cancelar **mexe no estoque** — e, a partir da Fatia 3, vai disparar estorno de pagamento. Uma
permissão única obrigaria a conceder o cancelamento para quem só precisa despachar pedido.

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
| `estoque` | O cancelamento devolve mercadoria com `adjustStock(ENTRADA)`; a liquidação de pedido online consome reserva |
| `ecommerce` | Vai criar o pedido de canal `MARKETPLACE` no checkout (Fatia 9) |
| `crm` | `customerId` alimenta o extrato do cliente (`CRM-F001`) |
| `financeiro` | Consome o pedido para DRE e provisão de cashback (`FIN-F001`) |

## Schema de Banco (Migrations)

| Migration | O que faz |
|---|---|
| V57 | Criou `cash_register_sale` e `sale_item` |
| **V65** | Renomeia para `sales_order` / `order_item`, acrescenta canal, status, numeração, cliente, desconto, cashback resgatado, troco, carimbos e `@Version`; cria `order_number_seq` |
| **V67** | Permissões `ORDER_READ`, `ORDER_FULFILL`, `ORDER_CANCEL` |

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

Itens relacionados vivem nos módulos que os originam: `PDV-F007` (marcar como reembolsado) em
[`vendas-balcao`](../vendas-balcao/README.md), `EST-F014` (estorno/devolução) em
[`estoque`](../estoque/README.md).

## Histórico de Implementações

- **2026-07-28** — `fundacao-do-pedido` (PDV-F003/F004/F005): `Order`/`OrderItem` substituem
  `Sale`/`SaleItem`; V65.
- **2026-07-28** — `orders-visao-do-administrador`: `OrderUseCase`/`OrderService`,
  `OrdersController`, quatro endpoints, V67. Coberto por `OrderServiceTest` e
  `OrdersControllerSecurityTest`.

## Próximos passos

- [ ] **PED-C001** — auditar o código e completar este README.
- [ ] Estorno de pagamento no cancelamento (depende da Fatia 3) e `REVERSED` de cashback (Fatia 4).
- [ ] `PDV-F007` — status `REEMBOLSADO`, distinto de `CANCELADO`.
