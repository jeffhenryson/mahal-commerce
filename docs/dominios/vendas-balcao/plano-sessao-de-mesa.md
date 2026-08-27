# Sessão de narguilé na mesa e canal `MESA` — plano de execução

**Criado em:** 2026-08-26.
**Origem:** `mahal-admin/Docs/PROMPT_BACKEND_SESSAO_MESA.md` — a especificação de contrato, escrita
pelo frontend. **Este arquivo é o roteiro de implementação aqui dentro:** a ordem, os arquivos e as
armadilhas que a especificação não tinha como conhecer.

> ## ✅ Implementado em 26/08/2026
>
> Os seis blocos abaixo estão escritos. Migrations **V112–V115**, 40 arquivos de `src/main`, mais os
> testes. **Não commitado** — está no working tree.
>
> As quatro decisões em aberto foram fechadas pelo dono: (1) a V113 **reclassifica** o histórico
> para `MESA`; (2) quando B fecha a mesa de A, o pedido entra na gaveta de **B**; (3) comanda só de
> cortesias **não fecha** (novo `COMANDA_ONLY_COURTESY`); (4) `sales_order.comanda_id` fica, com
> comentário explicando a redundância com `comanda.order_id`.
>
> **Não foi possível compilar nem rodar a suíte** — não há JDK neste ambiente. A verificação feita
> foi estática: aridade de construtor conferida programaticamente em `Product` (26 sites × 30 args),
> `Order` (14 × 26) e `Comanda` (5 × 11), mais checagem de que todo campo novo existe com acessor
> Lombok e todo tipo referenciado tem import. Rodar `./mvnw test` é o passo que falta.

**Estado original do levantamento:** 0 de 12 itens da checklist entregues. Conferido nos dois sentidos — o front checou
`GET /v3/api-docs` ao vivo em 26/08, e aqui `grep -rn "sessionProduct\|availableForTable\|
openRoshPrice\|sessionsPerUnit\|MESA" src/main/java` volta vazio, com
[`SalesChannel`](../../../src/main/java/com/cernecommerce/core/domain/model/pedido/SalesChannel.java)
tendo só `BALCAO` e `MARKETPLACE`.

O frontend inteiro já está escrito, testado e commitado atrás das flags `SESSAO_ENABLED`,
`MESA_ENABLED` e `ACAO_EM_MESA_ALHEIA_LIBERADA`. **Nenhuma tela precisa ser desenhada** — só o
contrato precisa existir.

---

## O que a especificação não sabia: quatro CHECKs da V65 barram `MESA`

Este é o achado que decide a ordem do plano. Adicionar `MESA` ao enum e mandar
`Order.openMesa(...)` não funciona: a
[`V65__pedido_sales_order.sql`](../../../src/main/resources/db/migration/V65__pedido_sales_order.sql)
gravou quatro invariantes de canal no schema, e três delas **rejeitam** um pedido de mesa.

| Constraint (V65) | Hoje | O que acontece com `MESA` |
|---|---|---|
| `ck_sales_order_channel` | `channel IN ('BALCAO','MARKETPLACE')` | ❌ insert falha direto |
| `ck_sales_order_customer_by_channel` | `channel = 'BALCAO' OR customer_id IS NOT NULL` | ❌ **exigiria cliente obrigatório na mesa** — e `customerId` é opcional por desenho |
| `ck_sales_order_session_by_channel` | `channel <> 'BALCAO' OR session_id IS NOT NULL` | ⚠️ passa, mas para de proteger: mesa **sempre** tem caixa e ficaria sem a amarra |
| `ck_sales_order_change_only_balcao` | `change_amount IS NULL OR = 0 OR channel = 'BALCAO'` | ❌ **mesa dá troco** — `closeComanda` chama `validatePaymentsAndComputeChange` |

As mesmas três regras estão duplicadas de propósito no compact constructor de
[`Order.java`](../../../src/main/java/com/cernecommerce/core/domain/model/pedido/Order.java)
(linhas 84, 90 e 110) — *"o domínio é a primeira barreira, o schema é a que sobrevive a carga
direta"*. **Os dois lados têm que mudar juntos**, ou o bug aparece só em produção, num `INSERT` que
o teste de domínio deixou passar.

---

## Ordem de execução

Seis blocos. Os blocos 1 e 2 são independentes entre si; 3 e 4 dependem dos dois; 5 e 6 são
independentes de tudo. Próximo número de migration livre: **V112**.

### Bloco 1 — Produto: os quatro campos novos → `V112`

Campo por campo, com o default que preserva o comportamento do catálogo já cadastrado:

```sql
ALTER TABLE product ADD COLUMN available_for_table BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE product ADD COLUMN session_product     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE product ADD COLUMN sessions_per_unit   INTEGER;
ALTER TABLE product ADD COLUMN open_rosh_price     NUMERIC(14,2);
ALTER TABLE product ADD CONSTRAINT ck_product_sessions_per_unit_positive
    CHECK (sessions_per_unit IS NULL OR sessions_per_unit > 0);
ALTER TABLE product ADD CONSTRAINT ck_product_open_rosh_price_non_negative
    CHECK (open_rosh_price IS NULL OR open_rosh_price >= 0);
```

**Molde a copiar:** `visible_in_pos` na
[`V91__estoque_product_catalog_fields_ii.sql`](../../../src/main/resources/db/migration/V91__estoque_product_catalog_fields_ii.sql).
O `DEFAULT TRUE` de `available_for_table` é obrigatório pelo mesmo motivo que o dela: é o único
jeito de todo produto já cadastrado preservar o comportamento implícito de hoje (sai na mesa).

**Os oito arquivos que `visible_in_pos` atravessa** — é literalmente a mesma lista, obtida com
`grep -rln visibleInPos src/main`:

`Product.java` · `ProductEntity.java` · `ProductRepositoryImpl.java` · `ProductDTOConverter.java` ·
`ProductRequest.java` · `ProductPatchRequest.java` · `ProductResponseDTO.java` · `EstoqueService.java`

> `availableForTable` e `sessionProduct` entram como **`Boolean` wrapper**, não primitivo — é o que
> `ProductRequest:110` já faz com `visibleInPos`, para "omitido" e "false" não virarem a mesma
> coisa no `PATCH`.

**Ponto extra, e é o que destrava o Bloco 4:** `EstoqueUseCase.CatalogSaleInfo`
([`EstoqueUseCase.java:630`](../../../src/main/java/com/cernecommerce/core/ports/in/EstoqueUseCase.java))
hoje é `(productName, pricing)`. Precisa passar a carregar `availableForTable`, `sessionProduct` e
`openRoshPrice` **do produto pai**. `EstoqueService.resolveSaleInfo:795` já resolve o pai via
`findByAnySku` — o dado está na mão, só não é devolvido.

Sabores não pedem nada: são as variantes da grade que já existem, e o `salePrice` de cada variante
já **é** o preço de sessão daquele sabor.

### Bloco 2 — Canal `MESA` no pedido → `V113`

1. `SalesChannel` ganha `MESA`, com javadoc no mesmo espírito dos outros dois: *venda de salão,
   sempre vinculada a uma sessão de caixa e a uma comanda; cliente opcional.*
2. `Order.openMesa(sessionId, warehouseCode, customerId, comandaId, tableLabel, items)`, espelhando
   `openBalcao:148` — nasce em `CRIADO`, quem chama conclui na mesma transação.
3. **Os três `if` do compact constructor** (a parte que não pode ser esquecida):
   - `:90` — `sessionId` obrigatório passa a valer para `BALCAO` **e** `MESA`
   - `:110` — troco passa a ser permitido em `BALCAO` **e** `MESA`
   - `:84` — sem mudança: `MARKETPLACE` continua sendo o único que exige cliente
4. Migration: `DROP` + `CREATE` das quatro constraints da tabela acima, e as duas colunas novas:

```sql
ALTER TABLE sales_order ADD COLUMN comanda_id  BIGINT REFERENCES comanda(id);
ALTER TABLE sales_order ADD COLUMN table_label VARCHAR(100);
CREATE INDEX idx_sales_order_comanda_id ON sales_order (comanda_id);
```

5. `comandaId` / `tableLabel` em `Order`, `OrderEntity`, `OrderRepositoryImpl`,
   `OrderAdminResponseDTO`, `OrderResponseDTO` e `OrderDTOConverter`.

> ⚠️ **`comanda.order_id` e `sales_order.comanda_id` apontam um para o outro.** É redundância
> deliberada (a comanda já guarda `order_id` desde a V104), mas o `closeComanda` grava os dois na
> mesma transação e nada impede que divirjam depois. Ou se aceita a redundância com um comentário
> explícito, ou o pedido lê a comanda por consulta reversa e dispensa a coluna. **Escolha, não
> default.**

**O que vem de graça neste bloco:** `GET /orders`, `/orders/analytics/summary`,
`/orders/analytics/top-products` e `/financeiro/margem` declaram o filtro como
`@RequestParam(required = false) SalesChannel channel`
([`OrdersController:107,132,153`](../../../src/main/java/com/cernecommerce/adapter/in/controller/OrdersController.java),
[`FinanceiroController:120`](../../../src/main/java/com/cernecommerce/adapter/in/controller/FinanceiroController.java)).
Spring liga o enum sozinho — os quatro passam a aceitar `channel=MESA` **sem uma linha de código**.
O item 10 da checklist fecha junto com o enum. Só confira se `getSummary`/`getTopProducts`/
`getMarginReport` não têm ramo por canal escondido.

**`GET /orders/{id}/receipt` também já está pronto:** `OrdersController:185` exige `ORDER_READ`, não
`PDV_READ`, e **não filtra por canal** — atende mesa no dia em que o canal existir. A única coisa a
mexer é o texto do `@Operation(summary = "…funciona para BALCAO e MARKETPLACE")` na linha 177. O
admin **já migrou** para esta rota (saiu de `/pdv/sales/{id}/receipt`), então ela é o caminho único
de recibo a partir de agora.

### Bloco 3 — Comanda com cliente → `V114` (parte 1)

```sql
ALTER TABLE comanda ADD COLUMN customer_id BIGINT REFERENCES customers(id);
```

`OpenComandaRequest.customerId` (opcional) → `ComandaService.openComanda` → `Comanda.open` →
`ComandaEntity` → `ComandaResponseDTO.customerId` + `customerName`.

Para o `customerName`, **reaproveite `enrichCustomerNames`**, que `OrdersController:173` já usa —
resolver nome dentro do converter puxaria o CRM para dentro do adapter de PDV.

No fechamento, o `customerId` da comanda passa a alimentar `Order.openMesa` no lugar do `null`
que `ComandaService:129` passa hoje — é isso que faz o pedido da mesa sair com nome e **gerar
cashback** (`cashbackUseCase.recordEarnedForOrder` já é chamado em `:139`, mas hoje sobre um pedido
sem cliente).

### Bloco 4 — `mode`, `courtesy`, `linkedItemId` → `V114` (parte 2)

O coração da entrega. Colunas em `comanda_item` **e** em `order_item` — sem as segundas, o histórico
da mesa no pedido não distingue cortesia de item cobrado, e inferir por preço zero mentiria (um
desconto de 100% dá o mesmo zero).

```sql
ALTER TABLE comanda_item ADD COLUMN mode           VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE comanda_item ADD COLUMN courtesy       BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE comanda_item ADD COLUMN linked_item_id BIGINT      REFERENCES comanda_item(id);
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_mode
    CHECK (mode IN ('NORMAL','OPEN_ROSH','SABOR_EXTRA','TROCA'));
-- Cortesia é preço zero por definição; o inverso não vale (promo pode zerar sem ser cortesia? não).
ALTER TABLE comanda_item ADD CONSTRAINT ck_comanda_item_courtesy_is_free
    CHECK (courtesy = FALSE OR unit_price = 0);
ALTER TABLE order_item ADD COLUMN mode     VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE order_item ADD COLUMN courtesy BOOLEAN     NOT NULL DEFAULT FALSE;
```

**A tabela de preço, que é o contrato:**

| `mode` | Linhas | `unitPrice` que o servidor grava |
|---|---|---|
| `NORMAL` | 1 | `pricing` da variante do sabor |
| `SABOR_EXTRA` sem promo | +1 linha própria | `pricing` da variante do segundo sabor |
| `SABOR_EXTRA` com promo | +1 linha própria | **0**, `courtesy = true` |
| `OPEN_ROSH` | 1 | **`openRoshPrice` do produto pai** |
| `TROCA` | 1 | **0**, `courtesy = true` |

> ⚠️ **A armadilha do open rosh.** A linha chega com o SKU da **variante** (ex.: `SESS-BLUE`,
> `salePrice` 35), mas o valor a cobrar é o `openRoshPrice` do **pai** (ex.: 60). `addItem` hoje faz
> `ComandaItem.fromCatalog(sku, quantity, saleInfo.pricing(), …)`
> ([`ComandaService:88`](../../../src/main/java/com/cernecommerce/core/service/ComandaService.java)),
> que resolve preço pelo SKU — e cobraria 35. **O SKU está ali para saber qual essência sair do
> estoque, não para precificar.** É por isso que o Bloco 1 precisa devolver `openRoshPrice` no
> `CatalogSaleInfo`.

> ⚠️ **Cortesia não é linha grátis para a contabilidade.** `unitPrice` e `netAmount` vão a zero, mas
> `costPrice` é congelado **normalmente**. É o que faz a margem do pedido mostrar o prejuízo real da
> promo e do open rosh — a pergunta de negócio por trás da feature ("o open rosh está dando
> lucro?"). Cuidado: `ComandaItem.fromCatalog` lança `ProductNotPricedException` quando
> `!pricing.isPriced()`; a cortesia precisa de uma fábrica própria — `ComandaItem.courtesy(...)` —
> que grave `ZERO` no preço e **preserve `pricing.costPrice()`**. Uma cortesia com custo nulo ou
> zerado mentiria.

`closeComanda:120-127` converte item a item com `OrderItem.of(...)` — a conversão passa a carregar
`mode` e `courtesy` junto. O comentário que já está lá (*"NUNCA por fromCatalog de novo:
reprecificar aqui repreçaria em silêncio itens que o cliente já consumiu"*) continua valendo e fica
ainda mais importante: o open rosh não teria como ser reprecificado corretamente.

`OrderItem` é `record` de 8 componentes, usado em `OrderService`, `PdvService`, `ShopService` e
`ComandaService` — acrescentar dois componentes mexe em todos os `of(...)`. Vale manter as
sobrecargas atuais delegando com `NORMAL`/`false`, como o próprio arquivo já faz em `:114-117` para
`productName`.

### Bloco 5 — Permissão de cortesia e erros tipados → `V115`

`PDV_COMANDA_COURTESY`, no molde da
[`V105`](../../../src/main/resources/db/migration/V105__pdv_comanda_permission.sql) +
[`V111`](../../../src/main/resources/db/migration/V111__pdv_comanda_permission_atendente.sql)
(`INSERT … ON CONFLICT DO NOTHING`, concedida a `ROLE_ADMIN` — **e não** a `ROLE_ATENDENTE`: lançar
linha a zero é desconto de 100%, no espírito de `PDV_SALE_DISCOUNT`).

Seis exceções novas em `core/domain/exception/pdv/`, no molde de uma linha de `ComandaEmptyException`,
com o `@ExceptionHandler` correspondente em
[`GlobalExceptionHandler.java:836`](../../../src/main/java/com/cernecommerce/infra/handler/GlobalExceptionHandler.java):

| Situação | HTTP | Código |
|---|---|---|
| `mode` de sessão em SKU que não é produto de sessão | 400 | `NOT_A_SESSION_PRODUCT` |
| `OPEN_ROSH` em produto sem `openRoshPrice` | 400 | `OPEN_ROSH_NOT_PRICED` |
| `courtesy: true` sem a permissão | 403 | `COURTESY_NOT_ALLOWED` |
| `SABOR_EXTRA`/`TROCA` sem `linkedItemId` válido na mesma comanda | 400 | `LINKED_ITEM_REQUIRED` |
| `TROCA` apontando para item que não é `OPEN_ROSH` | 409 | `NOT_AN_OPEN_ROSH` |
| SKU sem `availableForTable` lançado numa comanda | 400 | `NOT_AVAILABLE_FOR_TABLE` |

O último faz a regra *"bebida e narguilé saem na mesa, cigarro e isqueiro não"* existir no servidor
— hoje ela existiria só no cliente.

### Bloco 6 — Mesa compartilhada entre atendentes

`addItem:84`, `closeComanda:114` e `cancelComanda:150` chamam `pdvService.requireOwnOpenSession`,
que devolve **403 `SESSION_NOT_OWNED`**. Trocar por uma checagem que exija a sessão **aberta**, sem
exigir posse — o `@PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")` do controller já é o
controle de acesso.

Na prática: um `requireOpenSession(sessionId)` novo em `PdvService`, ao lado do
`requireOwnOpenSession`, que continua servindo `registerSale` e os movimentos de caixa — **ali a
gaveta é individual e a posse fica**.

> **Não é regressão do PDV-C004.** Aquele isolamento resolveu o buraco da *venda de balcão*, onde
> vender no caixa alheio criava diferença sem dono. Mesa é outro caso: o consumo é do salão, não do
> operador. Precedente já aceito no projeto: a lista de reposição, *"por armazém e compartilhada
> entre operadores"*.

**Alternativa que o front sugere, se houver apetite:** `GET /pdv/comandas` sem `sessionId` (ou com
`?warehouseCode=`), devolvendo as mesas abertas da loja. Hoje o cliente faz `GET /pdv/sessions` e
mescla as comandas de cada sessão aberta — um N+1 que uma chamada só resolveria. Fora da checklist,
mas é a limpeza natural depois deste bloco.

---

## Quatro decisões que precisam de dono antes do código

Nenhuma tem resposta certa. O que não pode é serem escolhidas por acidente.

1. **Backfill de `channel`.** Pedidos já gerados por fechamento de comanda estão gravados como
   `BALCAO`. Como o canal é **imutável** por desenho, ou a V113 os reclassifica cruzando com
   `comanda.order_id` (`UPDATE sales_order SET channel = 'MESA' … WHERE id IN (SELECT order_id FROM
   comanda WHERE order_id IS NOT NULL)`), ou o histórico anterior fica `BALCAO` para sempre. A V65
   tem precedente de backfill com heurística explicada — se for reclassificar, siga aquele padrão de
   comentário.
2. **Redundância `comanda.order_id` ↔ `sales_order.comanda_id`** (ver o aviso do Bloco 2).
3. **Comanda só de cortesias (total R$ 0) pode fechar?** Hoje ela recusa fechar vazia
   (`ComandaEmptyException` → 409 `COMANDA_EMPTY`), mas uma comanda com três linhas de cortesia e
   total zero **passa**. Pelo desenho do open rosh ela nunca deveria existir — a regra precisa ser
   explícita, num sentido ou no outro.
4. **Com a posse relaxada (Bloco 6), quando B fecha a mesa de A, qual caixa recebe?** Hoje seria o
   de A, porque `closeComanda` não recebe sessão e usa a da comanda. As duas leituras são
   defensáveis: o consumo pertence ao turno em que começou, ou o dinheiro pertence a quem o recebeu.

E uma quinta que só aparece se alguém criar remover-item-de-comanda (endpoint que **não existe**
hoje): remover uma linha `OPEN_ROSH` teria de arrastar as `TROCA` ligadas a ela pelo `linkedItemId`.

---

## Testes

Cobertura atual: 238 arquivos em `src/test`. Os que vão precisar de caso novo:

| Arquivo | O que acrescentar |
|---|---|
| `core/domain/model/pedido/OrderTest.java` | `openMesa` com e sem cliente; troco em `MESA` permitido; `sessionId` nulo em `MESA` recusado |
| `core/domain/model/pdv/ComandaItemTest.java` | `courtesy(...)` gravando preço zero **com custo preservado** |
| `core/service/ComandaServiceTest.java` | a tabela de preço inteira — em especial **open rosh cobrando o pai, não a variante** |
| `core/service/ComandaCashCycleIT.java` | ciclo completo: abre mesa com cliente → sessão + duplo com cortesia → fecha → pedido nasce `MESA` com `comandaId`/`tableLabel` e cashback do cliente |
| `adapter/in/controller/PdvComandaControllerSecurityTest.java` | `COURTESY_NOT_ALLOWED` sem a permissão; mesa alheia **passando** depois do Bloco 6 |
| `adapter/out/persistence/repository/ComandaRepositoryIT.java` | ida e volta de `mode`/`courtesy`/`linkedItemId`/`customerId` |

Lembrete de ambiente: **não há `@DataJpaTest` no classpath** (Spring Boot 4) — os testes de
persistência deste repositório usam `@SpringBootTest` + `@Transactional`.

```
./mvnw test
```

---

## Checklist do `PROMPT_BACKEND_SESSAO_MESA.md`, mapeada nos blocos

| # | Item | Bloco |
|---|---|---|
| 1 | 4 campos em `ProductRequest`/`ProductPatchRequest`/`ProductResponseDTO` + migration | 1 |
| 2 | `POST /pdv/comandas/{id}/items` aceita `mode`/`courtesy`/`linkedItemId` e recusa SKU sem `availableForTable` | 4 + 5 |
| 3 | Open rosh cobra o `openRoshPrice` do pai | 1 + 4 |
| 4 | Cortesia grava `unitPrice = 0` com `costPrice` congelado | 4 |
| 5 | `ComandaItemResponseDto` devolve os três | 4 |
| 6 | `OrderItemAdminResponseDto` devolve `mode` e `courtesy` | 4 |
| 7 | `addItem`/`close`/`cancel` aceitam operador que não é dono do caixa | 6 |
| 8 | `OpenComandaRequest.customerId`; resposta com `customerId`/`customerName` | 3 |
| 9 | Comanda fechada gera pedido `MESA` com `comandaId` e `tableLabel` | 2 + 3 |
| 10 | `GET /orders`, `/orders/analytics/*` e `/financeiro/margem` aceitam `channel=MESA` | 2 — **de graça** |
| 11 | `GET /orders/{id}/receipt` atende mesa | 2 — **só o texto do `@Operation`** |
| 12 | Permissão própria para cortesia | 5 |

`openapi.json` não é gerado neste repositório: o front roda `npm run fetch:spec` contra a API ao
vivo. Basta o backend estar de pé com as mudanças.

---

## O que o frontend faz depois

1. `npm run fetch:spec && npm run generate:api` — **nesta ordem**. O `openapi.json` versionado no
   admin está parcial (56 paths, sem `/orders` e sem `/pdv/comandas`, contra 165 na API ao vivo);
   gerar a partir do arquivo local apagaria metade de `src/app/api/fn/`.
2. Virar `MESA_ENABLED` e `SESSAO_ENABLED` em `src/app/features/estoque/estoque.models.ts`.
3. Remover as *type augmentations* locais: `ProdutoComMesa`, `ProdutoComSessao`,
   `AddComandaItemComSessao`, `ComandaItemComSessao`, `OpenComandaComCliente`, `PedidoComMesa`,
   `PedidoItemComSessao`.
4. Virar `ACAO_EM_MESA_ALHEIA_LIBERADA` (`pdv/sessao-mesa.models.ts`) quando o Bloco 6 chegar.

## Fora de escopo, explicitamente

**Promoção por calendário não se implementa.** Domingo é *free hosh* e quarta é *double hosh*, mas
quem aplica é o atendente, marcando a cortesia na tela. O backend precisa **aceitar e registrar** a
linha de cortesia — não decidir quando ela vale.

**Quantidade fracionada também não.** A alternativa seria manter o saldo em latas e baixar `1/N` por
sessão; todo campo de quantidade do sistema é inteiro hoje, então isso mudaria o contrato de ajuste,
contagem e reposição de uma vez. Lata e sessão são **SKUs distintos**, com conversão explícita por
`POST /estoque/movements` — que já existe e **já está em produção** no admin
(`estoque-conversao.dialog.ts`). `sessionsPerUnit` é só a sugestão que aquele diálogo lê; não
movimenta saldo sozinho.

Nada em Compras, CRM, Logística ou E-commerce muda.
