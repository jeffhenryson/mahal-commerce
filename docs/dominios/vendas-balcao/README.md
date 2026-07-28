# Domínio: vendas-balcao (PDV — Frente de Caixa)

**Status:** 🟢 Operacional — ciclo de caixa completo (abertura, sangria/suprimento, fechamento com conferência) e venda com preço vindo do catálogo. Falta pagamento com múltiplas formas (PDV-F006, Fatia 3).
**Pacote Java:** `com.cernecommerce...pdv` (packages não aceitam hífen; `pdv` ↔ `vendas-balcao`)
**Rota HTTP base:** `/pdv`
**Última atualização deste doc:** 2026-07-28 (Fatia 1 — ciclo de caixa e superfície `/orders`)

> ⚠️ **Auditoria de código pendente (PDV-C001).** As seções de Regras de Negócio, Schema e
> Cobertura de Testes ainda precisam ser preenchidas a partir do código — rode
> `/1-analise vendas-balcao`. Padrão: [`estoque`](../estoque/README.md). A API e a Segurança já
> refletem a Fatia 1.

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
| `GET` | `/pdv/pending-online-orders` | `PDV_READ` | Pedidos do app aguardando pagamento, para o caixa localizar quem chegou na loja |
| `POST` | `/pdv/sessions/{id}/orders/{orderId}/settle` | `PDV_SALE_MANAGE` | Liquida no balcão um pedido do app: consome a reserva e conclui |
| `POST` | `/pdv/sessions/{id}/sales` | `PDV_SALE_MANAGE` (+ `PDV_SALE_DISCOUNT` se houver desconto) | Registra venda na sessão e **dá baixa no estoque** item a item. Preço e custo vêm do catálogo, não do request. Exige sessão `OPEN` |
| `GET` | `/pdv/sales/{id}` | `PDV_READ` | Consulta um pedido. Antes de PDV-F005 a venda era write-only |
| `GET` | `/pdv/sessions/{id}/sales` | `PDV_READ` | Pedidos da sessão, paginados, do mais recente para o mais antigo |

> **Contrato alterado em PDV-F004** (sem consumidor real — o PDV do `frontend-admin` é protótipo
> mockado): `unitPrice` saiu do corpo de `POST /pdv/sessions/{id}/sales`, `discountAmount` e
> `customerId` entraram. Produto sem preço recusa a venda com `409 PRODUCT_NOT_PRICED`; desconto
> acima do teto (`pdv.sale.max-discount-percent`, default 10%) responde `409
> DISCOUNT_LIMIT_EXCEEDED`. Detalhes em [`docs/api-reference.md`](../../api-reference.md#pdv-vendas-balcão--pdv).

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
- **PDV-F006** — o `expectedAmount` do fechamento soma todas as formas de pagamento enquanto
  `order_payment` não existir (Fatia 3).
- **PLAT-C030** — sem rate limit.

## Integração com estoque

`PdvService.registerSale` (`core/service/PdvService.java:47`) chama
`EstoqueUseCase.adjustStock(..., MovementType.SAIDA, ...)` para cada item, com o motivo
`Venda balcão sessão #{sessionId}`, e só então persiste a `Sale` — tudo na mesma transação.
Saldo insuficiente em qualquer item reverte a venda inteira. A baixa também dispara o alerta
de ponto de reposição. Detalhes em [`estoque`](../estoque/README.md#integrações-entre-domínios).

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

> **Pré-requisitos:** a abertura de caixa ainda não tem endpoint — abra uma sessão pelo banco
> (SQL na descrição da coleção). E, no profile `dev` (H2, sem Flyway), a permissão
> `PDV_SALE_MANAGE` não é semeada: o registro de venda responde 403 até ela ser concedida —
> é o item EST-C001 do backlog de estoque.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| PDV-F006 | 🟡 Média | Feature | pagamento-multiplas-formas-e-troco | `order_payment` (V67) com uma linha por forma — dinheiro + cartão no mesmo pedido são duas linhas. Troco é `change_amount` no pedido, **não** linha de pagamento negativa. Fechamento de caixa reporta totais por forma; só `DINHEIRO` entra na conferência da gaveta. §2.6. | Pendente |
| PDV-F007 | 🟢 Baixa | Feature | marcar-pedido-como-reembolsado | Status `REEMBOLSADO`, distinto de `CANCELADO`, com estorno do pagamento e `REVERSED` no ledger de cashback. Cancelar e reembolsar são eventos diferentes: contá-los juntos esconde quanto dinheiro de fato voltou ao cliente. Depende da Fatia 3 (`order_payment`) e da Fatia 4 (cashback) — antes disso não há o que estornar. Acréscimo de enum + `CHECK`, barato quando as duas existirem. Levantado com o dono em 2026-07-28. | Pendente |
| PDV-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Preencher Regras de Negócio, Schema (V57) e Cobertura de Testes no padrão de `estoque`. | Pendente |

> A permissão `PDV_SALE_MANAGE` está ausente dos seeders de dev — rastreado como
> **EST-C001** em [`estoque`](../estoque/README.md#backlog-do-módulo), porque o sintoma
> aparece no fluxo de baixa de estoque.

## Histórico de Implementações

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
- [ ] **PDV-F001 + PDV-F002 + PDV-C004** — Fatia 1: ciclo de caixa, a maior lacuna operacional —
      hoje abrir caixa exige `INSERT` manual.
- [ ] **PDV-F006** — Fatia 3: pagamento com múltiplas formas e troco.
- [ ] **PDV-C001** — auditar o código e completar este README.
