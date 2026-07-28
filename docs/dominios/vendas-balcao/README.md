# Domínio: vendas-balcao (PDV — Frente de Caixa)

**Status:** 🟡 Parcial — registro de venda com baixa de estoque operacional; ciclo de caixa (abertura/sangria/fechamento) ainda em esqueleto
**Pacote Java:** `com.cernecommerce...pdv` (packages não aceitam hífen; `pdv` ↔ `vendas-balcao`)
**Rota HTTP base:** `/pdv`
**Última atualização deste doc:** 2026-07-27 (seção de Segurança e Infraestrutura)

> ⚠️ **Auditoria de código pendente.** Este README foi atualizado em 2026-07-26 apenas para
> refletir a entrega de `EST-F010` e receber o backlog do módulo. As seções de Regras de
> Negócio, API completa, Schema e Cobertura de Testes ainda precisam ser preenchidas a partir
> do código — rode `/1-analise vendas-balcao`. Padrão: [`estoque`](../estoque/README.md).

## Objetivo

Frente de caixa (PDV) das vendas locais da tabacaria: controle de fluxo de caixa e
registro de vendas no balcão.

## Escopo planejado

- **Fluxo de caixa:** abertura de caixa, sangria (retirada), suprimento e
  fechamento com conferência (valor esperado × contado). 🟡 Pendente — `CashRegisterSession`
  é um stub com o enum `Status {OPEN, CLOSED}`, sem regras `open()`/`close()`.
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

Single-tenant e **sem vínculo operador↔caixa**: qualquer usuário com `PDV_SALE_MANAGE` registra
venda em **qualquer** sessão de caixa aberta, inclusive na de outro operador. O `username` fica
no `stock_movement` gerado pela baixa, mas a `Sale` em si não amarra a autoria à sessão. Fechar
isso faz parte de PDV-F001.

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

- **PDV-F001** — sem ciclo de caixa: sessões abertas fora do sistema, sem conferência.
- **PDV-C002** — `GET /pdv/sessions` devolve o record de domínio direto, sem DTO.
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
| PDV-F001 | 🔴 Alta | Feature | ciclo-de-caixa | `openSession`, `registerWithdrawal` (sangria), suprimento e `closeSession` com conferência (esperado × contado). Hoje só é possível registrar venda numa sessão que nada no sistema sabe abrir. TODO em `core/ports/in/PdvUseCase.java:13` e `adapter/in/controller/PdvController.java:39`. | Pendente |
| PDV-F002 | 🟡 Média | Feature | modelos-de-movimento-de-caixa | `CashMovement` (sangria/suprimento) e `CashRegisterClosure`; dar regras de negócio ao stub `CashRegisterSession` (`core/domain/model/pdv/CashRegisterSession.java:10`). | Pendente |
| PDV-F003 | 🔴 Alta | Feature | unificar-venda-em-pedido | Unificar `Sale`/`SaleItem` em `Order`/`OrderItem` com discriminador de canal (`BALCAO`/`MARKETPLACE`) e máquina de estados; migration V64 renomeia `cash_register_sale → sales_order` e `sale_item → order_item`. Precede todo o resto porque é a única mudança que fica mais cara a cada dia de dado real. Fatia 0 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.1. | 🚧 Em andamento — domínio escrito (`core/domain/model/pedido`), falta service, persistência, controller e migration |
| PDV-F004 | 🔴 Alta | Feature | preco-e-custo-congelados-no-item | `unitPrice` sai de `SaleItemRequest` — o servidor resolve via `EstoqueUseCase.findPricingBySku`. O item passa a congelar `unit_price`, `cost_price` (snapshot, sem ele a margem histórica é reescrita pela próxima compra) e `cashback_percent`. Desconto vira `discountAmount` explícito sob `PDV_SALE_DISCOUNT`, com teto em `system_config`. Quebra de contrato deliberada em `POST /pdv/sessions/{id}/sales` — **sem consumidor real**: o PDV do `frontend-admin` é protótipo mockado. §2.3. | 🚧 Em andamento — `OrderItem.fromCatalog` já resolve preço e custo do `Pricing` |
| PDV-F005 | 🔴 Alta | Feature | leitura-de-venda | `SaleRepository` (`core/ports/out/pdv/SaleRepository.java:8-11`) expõe só `save()` — venda registrada é write-only, não há como relê-la pela API. Adicionar `findById`, listagem por sessão e por cliente, com `GET /pdv/sales/{id}` e `GET /pdv/sessions/{id}/sales`. | Pendente |
| PDV-F006 | 🟡 Média | Feature | pagamento-multiplas-formas-e-troco | `order_payment` (V67) com uma linha por forma — dinheiro + cartão no mesmo pedido são duas linhas. Troco é `change_amount` no pedido, **não** linha de pagamento negativa. Fechamento de caixa reporta totais por forma; só `DINHEIRO` entra na conferência da gaveta. §2.6. | Pendente |
| PDV-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Preencher Regras de Negócio, Schema (V57) e Cobertura de Testes no padrão de `estoque`. | Pendente |
| PDV-C002 | 🟢 Melhoria | Correção | expor-dto-em-vez-de-record-de-dominio | `GET /pdv/sessions` retorna `PageResult<CashRegisterSession>` — record de domínio direto na API, sem DTO. | Pendente |
| PDV-C004 | 🔴 Alta | Correção | amarrar-sessao-ao-operador-na-venda | `PdvService.registerSale` (`:40-44`) valida que a sessão está `OPEN`, **não** que pertence a quem está vendendo. Com o ciclo de caixa, a venda passa a exigir sessão do próprio operador (`403 SESSION_NOT_OWNED`) e o `warehouseCode` passa a vir da sessão em vez do request — é esse escopo, não permissão fina de estoque, que fecha o isolamento documentado na seção *Integração com estoque* acima. §1.4 e §2.7. | Pendente |

> A permissão `PDV_SALE_MANAGE` está ausente dos seeders de dev — rastreado como
> **EST-C001** em [`estoque`](../estoque/README.md#backlog-do-módulo), porque o sintoma
> aparece no fluxo de baixa de estoque.

> **PDV-F001** e **PDV-F002** são a Fatia 1 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md)
> §2.7, que detalha o modelo: `CashRegisterSession` com `open`/`closedWith`, `cash_movement`
> (`SANGRIA`/`SUPRIMENTO`), índice parcial único garantindo uma sessão aberta por operador, e
> fechamento que registra divergência sem bloquear — espelhando `StockCount`. `findOpenByOperator`
> já existe em `CashRegisterRepository:15` e nunca foi chamado.

## Histórico de Implementações

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
