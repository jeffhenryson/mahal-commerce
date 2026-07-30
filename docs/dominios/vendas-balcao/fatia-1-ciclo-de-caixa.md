# Fatia 1 — Ciclo de caixa + superfície `/orders` do administrador

**Criado em:** 2026-07-28. **Escopo aprovado com o dono do produto nesta data.**
**Roteiro geral do módulo:** [`proximos-passos.md`](proximos-passos.md).
**Desenho das decisões:** [`docs/plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).

Esta fatia é maior que a Fatia 1 original do plano: por decisão do dono, ela **antecipa a Fatia 5**
(visão `/orders` do administrador, com transição de estágio e cancelamento com estorno de estoque) e
acrescenta um fluxo novo — **liquidar no balcão um pedido feito no aplicativo**.

Estimativa: **9–11 dias** (4–5 do ciclo de caixa, 4–5 do `/orders`, ~1 da liquidação cruzada).

---

## Onde o projeto está — leia antes de tudo

| Superfície | Estado em 2026-07-28 |
|---|---|
| **PDV registra pedido** | ✅ Implementado na Fatia 0 — `POST /pdv/sessions/{id}/sales`, `GET /pdv/sales/{id}`, `GET /pdv/sessions/{id}/sales`. ⚠️ **Não verificado**: a suíte não roda desde que a camada de service/persistência/controller foi escrita. Os 1316 testes que passaram cobriam só o domínio. |
| **PDV abre/fecha caixa** | ❌ Não existe — é esta fatia. Hoje abrir caixa exige `INSERT` manual. |
| **Admin vê e gerencia pedidos** | ❌ Não existe — é esta fatia. |
| **Marketplace cria pedido** | ❌ **Nada cria um pedido `MARKETPLACE` hoje.** O domínio aceita o canal e a máquina de estados cobre o fluxo inteiro, mas não existe `/shop`, autenticação de cliente final, carrinho nem checkout. São as Fatias 8–10. |
| **Cliente vê a própria compra** | ❌ Precisa de `/shop/orders` (Fatia 8/9). Esta fatia entrega o equivalente pelo lado do admin: `GET /orders?customerId=`. |

## Decisões tomadas com o dono em 2026-07-28

**1. Pedido online liquidado no balcão continua `MARKETPLACE`.**
`channel` passa a significar *origem* (imutável); `sessionId` passa a significar *qual caixa
liquidou*. São dimensões independentes. Reescrever o canal para `BALCAO` na liquidação faria o
relatório de conversão do site mentir — todo pedido retirado na loja sumiria das vendas do
marketplace.
✅ **Já aplicado**: invariante removida em `Order.java`, `CHECK` ajustado na V65, `OrderTest`
atualizado.

**2. `warehouseCode` sai do corpo da venda e passa a vir da sessão de caixa** (PDV-C004).
Segunda quebra de contrato de `POST /pdv/sessions/{id}/sales`, ainda sem consumidor real — o PDV do
`frontend-admin` é protótipo mockado (`pdv.mock-data.ts`: *"Sem integração com o backend ainda"*).

---

## Passo 0 — Bloqueante ✅ resolvido

O risco apontado aqui era `nextval('order_number_seq')` em H2 modo PostgreSQL
(`OrderJpaRepository.nextOrderNumber`). **Ele se materializou** em 2026-07-28: `PedidoRepositoryIT`
falhou com *Sequence "ORDER_NUMBER_SEQ" not found*.

A causa não era o dialeto — era `src/test/resources/application-dev.properties` **substituindo** o
de `src/main/resources/`, de modo que o `spring.sql.init` que cria a sequência nunca era lido nos
testes. Corrigido replicando as três propriedades no arquivo de teste. Ver *Armadilhas descobertas
durante esta fatia*, abaixo.

Vale notar o que esse episódio revelou: **sem o `PedidoRepositoryIT`, a suíte inteira passava** —
1324 testes verdes — com a numeração de pedido quebrada em todo ambiente. O unitário mocka o
repositório e o teste de segurança para em 404 antes de chegar na sequência.

---

## Parte A — Modelo ✅ concluída

- `core/domain/model/pedido/Order.java` — removida a invariante
  `channel == MARKETPLACE → sessionId == null`. Mantida `channel == BALCAO → sessionId != null`.
- `V65__pedido_sales_order.sql` — `ck_sales_order_session_by_channel` virou
  `CHECK (channel <> 'BALCAO' OR session_id IS NOT NULL)`.
- `OrderTest.marketplaceAcceptsCashRegisterSessionWhenSettledAtTheCounter` afirma o novo contrato.

> A V65 foi **editada**, não corrigida por uma migration nova. A regra do repositório é não editar
> migration *aplicada*; a V65 é untracked, nunca foi commitada nem rodou contra Postgres. Corrigir
> agora custou uma linha; depois custaria uma migration existindo só para desfazer um `CHECK`.

---

## Parte B — Ciclo de caixa (PDV-F001, PDV-F002, PDV-C002, PDV-C004)

**Espelhar `StockCount`**, que já resolveu o mesmo problema com dinheiro no lugar de mercadoria: o
fechamento confronta contado × esperado, carimba a divergência e **fecha mesmo assim**. Ver
`EstoqueService.closeStockCount` e `StockCountItem.diverges()`
(`core/domain/model/estoque/StockCountItem.java:49`).

### Migration `V66__pdv_cash_cycle.sql`

- `cash_register_session` ganha `warehouse_code` (NOT NULL após backfill pelo primeiro `warehouse`
  de `type = 'LOJA_FISICA'`), `closed_by`, `expected_amount`, `counted_amount`, `difference_amount`.
- **Índice parcial único** — o banco garante o que o domínio promete:
  ```sql
  CREATE UNIQUE INDEX uk_cash_register_session_open_operator
      ON cash_register_session (operator) WHERE status = 'OPEN';
  ```
- `CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND counted_amount IS NOT NULL))`
- Tabela `cash_movement (id, session_id, type, amount, reason, username, created_at)` com
  `CHECK (amount > 0)` e `type IN ('SANGRIA','SUPRIMENTO')` — **o sinal vem do tipo, não do valor**.
- Permissões `PDV_SESSION_MANAGE` e `PDV_SESSION_CLOSE`, com `ON CONFLICT DO NOTHING`.

### Domínio

- **`CashRegisterSession`** — hoje é um record sem invariantes, sem fábricas e sem comportamento.
  Reescrever no padrão do repositório: compact constructor, `open(operator, openingAmount,
  warehouseCode)`, `of(...)`, `closedWith(expected, counted, closedBy)`, `isOpen()`, `diverges()`.
  `differenceAmount == countedAmount − expectedAmount`, **podendo ser negativo** — falta no caixa é
  um número legítimo, não um erro de validação.
- **`CashMovement`** (novo) + enum `CashMovementType { SANGRIA, SUPRIMENTO }`.

### Portas e persistência

- `CashRegisterRepository.findOpenByOperator` **já existe e nunca foi chamado**
  (`core/ports/out/pdv/CashRegisterRepository.java:15`, implementado em
  `CashRegisterRepositoryImpl.java:34`). Aproveitar, não recriar.
- Novo `CashMovementRepository` + entity + JPA repo + impl, no molde de `OrderRepositoryImpl`.
- `CashRegisterSessionEntity` ganha as colunas novas.

### Casos de uso

| Método | Regra |
|---|---|
| `openSession(operator, openingAmount, warehouseCode)` | 409 `SESSION_ALREADY_OPEN` se já houver uma aberta para o operador |
| `getCurrentSession(operator)` | 404 `NO_OPEN_SESSION` |
| `registerCashMovement(sessionId, type, amount, reason, username)` | exige sessão `OPEN` **e do próprio operador** |
| `closeSession(sessionId, countedAmount, username)` | divergência **não bloqueia o fechamento** |
| `registerSale(...)` | **muda**: `warehouseCode` sai da assinatura e vem da sessão; passa a exigir sessão do próprio operador (403 `SESSION_NOT_OWNED`) |

> **`expected` é aproximação nesta fatia.** A fórmula correta é
> `openingAmount + vendas em DINHEIRO − sangrias + suprimentos`, mas `order_payment` só existe na
> Fatia 3 — não há como saber a forma de pagamento. Por ora o esperado usa o **líquido de todos os
> pedidos concluídos na sessão**. **Documentar no README**, senão a conferência da gaveta vai parecer
> quebrada no dia em que a primeira venda no cartão entrar.

### API

`POST /pdv/sessions` · `GET /pdv/sessions/current` · `GET /pdv/sessions/{id}` ·
`POST /pdv/sessions/{id}/movements` · `POST /pdv/sessions/{id}/close`

Aproveitar para fechar **PDV-C002**: `GET /pdv/sessions` devolve o record de domínio direto na borda
HTTP; passa a devolver `CashRegisterSessionResponseDTO`.

---

## Parte C — `/orders`: a visão do administrador

Novo `OrderUseCase` + `OrderService` em `core/service` e `OrdersController` — **fora do PDV**, porque
a superfície atravessa canais.

| Rota | Permissão | Regra |
|---|---|---|
| `GET /orders` | `ORDER_READ` | filtros por `channel`, `status`, período e `customerId`; paginado |
| `GET /orders/{id}` | `ORDER_READ` | detalhe **com custo e margem** (`OrderItem.marginAmount()`), ao contrário do DTO do PDV, que os omite de propósito |
| `POST /orders/{id}/status` | `ORDER_FULFILL` | `SEPARADO`/`ENVIADO`/`ENTREGUE`; 409 `INVALID_STATUS_TRANSITION` |
| `POST /orders/{id}/cancel` | `ORDER_CANCEL` | estorna estoque com `adjustStock(ENTRADA)` por item, na mesma transação |

`OrderRepository` ganha `findAll(filtros, page, size)`. As transições usam
`OrderStatus.canTransitionTo` e `Order.withStatus`/`Order.cancelled`, que **já existem e já têm
teste** — o service só orquestra e persiste.

O cancelamento é **EST-F014** (estorno/devolução) chegando pela porta do pedido. O `reason` do
`stock_movement` precisa carregar o `orderNumber`, senão a trilha não é reconstruível.

Migration `V67__order_admin_permissions.sql`: `ORDER_READ`, `ORDER_FULFILL`, `ORDER_CANCEL`.

---

## Parte D — Liquidar no balcão um pedido feito no app

- `GET /pdv/pending-online-orders` (`PDV_READ`) — pedidos `MARKETPLACE` em `AGUARDANDO_PAGAMENTO`,
  para o caixa localizar o cliente que acabou de chegar na loja.
- `POST /pdv/sessions/{id}/orders/{orderId}/settle` (`PDV_SALE_MANAGE`) — vincula o pedido à sessão,
  conclui e emite o `orderNumber`.

**O estoque desse pedido já está reservado**, então a liquidação **consome a reserva** via
`EstoqueUseCase.consumeReservation` — e **não** chama `adjustStock(SAIDA)`, que baixaria duas vezes.
A porta já existe (EST-F021).

> **Limite honesto:** como nada cria pedido `MARKETPLACE` hoje, este fluxo só é exercitável por teste
> (montando o pedido direto pelo repositório num IT). De ponta a ponta ele só vive quando o checkout
> existir (Fatia 9). Vale construir agora mesmo assim: a alternativa é retrofitar a invariante de
> `sessionId` depois, que é exatamente o tipo de mudança que fica cara com dado real acumulado.

---

## Auditoria e seeds

Acrescentar a `AuditEvent.EventType`: `CASH_SESSION_OPENED`, `CASH_SESSION_CLOSED`,
`CASH_MOVEMENT_REGISTERED`, `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED`. Publicados **nos
controllers** — `HexagonalArchitectureTest` barra `ApplicationEventPublisher` em `core/service`, e
`PdvController` já é o molde.

Toda permissão nova entra também em `SeedConfig` e `DevRoleBootstrapConfig`. O perfil `dev` não roda
Flyway; esquecer isso dá 403 em dev e já aconteceu três vezes neste projeto.

---

## Anotado para depois — não entra nesta fatia

- **`PDV-F007` — marcar como reembolsado e estornar o cashback.** O cancelamento desta fatia já
  estorna estoque. O estorno de *pagamento* depende da Fatia 3 (`order_payment`) e o `REVERSED` do
  cashback depende da Fatia 4. Um status `REEMBOLSADO` distinto de `CANCELADO` é acréscimo barato de
  enum + `CHECK` quando as duas existirem — cancelamento e reembolso são eventos diferentes, e
  contá-los juntos esconderia quanto dinheiro de fato voltou.
- **Cliente vendo a própria compra no app** — `/shop/orders`, com `customerId` resolvido do principal
  autenticado e **404, não 403**, para pedido alheio. Fatias 8/9 (`ECM-F001`/`ECM-F003`).
- **`GET /crm/customers/{id}/orders` real** (`CRM-F001`) — deixa de ser placeholder assim que existir
  a consulta por cliente, que a Parte C já constrói em `OrderRepository`.

---

## Verificação

1. `./mvnw test` — suíte inteira. O raio desta fatia alcança `GlobalExceptionHandler`,
   `CoreBeanConfig`, `SeedConfig` e o perfil `dev`.
2. Testes no padrão do módulo: unitário de domínio (`CashRegisterSessionTest`, `CashMovementTest`),
   service com Mockito (`PdvServiceTest`, `OrderServiceTest`), controller com MockMvc,
   `*SecurityTest` cobrindo 401/403/sucesso por endpoint novo, IT com `@SpringBootTest` +
   `@Transactional`.
3. **IT do ciclo completo**, que é o que prova a fatia: abre caixa → vende 2 itens → sangria → fecha
   com valor contado divergente → confere `expected`, `counted`, `difference`, e que a sessão fechou
   **apesar** da divergência.
4. **IT de concorrência**: duas aberturas simultâneas para o mesmo operador — uma tem que falhar no
   índice parcial único, não só no domínio.
5. Collection Postman de `vendas-balcao` atualizada (validar o JSON) e uma nova para `/orders`.
6. `docs/api-reference.md`, README do módulo, `docs/feature-registry.md` e a data no topo do README.

Ao concluir: mover PDV-F001, PDV-F002, PDV-C002 e PDV-C004 do backlog para o Histórico, e registrar
EST-F014 como parcialmente entregue (estorno pela porta do cancelamento de pedido).

## Armadilhas descobertas durante esta fatia

- **`src/test/resources/application-dev.properties` SUBSTITUI o de `src/main/resources/`**, não
  herda. Propriedade nova que o teste precisa tem que ser escrita nos **dois** arquivos. Já custou
  uma rodada de suíte em 2026-07-28 (a sequência `order_number_seq` não existia no H2 porque o
  `spring.sql.init` só estava no `main`).
- **A suíte NÃO executa as migrations.** O perfil `dev` tem `spring.flyway.enabled=false` e monta o
  schema por `ddl-auto=create-drop` a partir das entities. Consequência: o SQL de V63, V64, V65 e V66
  nunca rodou em lugar nenhum. Um erro de sintaxe ou uma coluna divergente entre entity e migration
  só aparece no primeiro deploy contra Postgres.

## Riscos

| Risco | Mitigação |
|---|---|
| ~~A Fatia 0 nunca foi testada além do domínio~~ ✅ | Resolvido — suíte completa verde (1485 testes) desde 2026-07-29 |
| ~~`expected` sem `order_payment` é aproximação~~ ✅ | Resolvido — Fatia 3 (PDV-F006) fechou em 2026-07-29; `expected` agora soma só `DINHEIRO` capturado |
| Cancelar pedido já entregue devolve ao estoque mercadoria que fisicamente saiu | `ENTREGUE → CANCELADO` **é** devolução, e devolução é entrada de estoque. O comportamento está certo; o que precisa estar certo é o `reason` do movimento dizer que foi devolução, e não venda estornada |
| Não commitar | O usuário commita manualmente; deixar tudo no working tree |
