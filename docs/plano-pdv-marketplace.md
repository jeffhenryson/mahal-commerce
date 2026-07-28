# Plano arquitetural — PDV + Marketplace

**Data:** 2026-07-28
**Escopo:** tornar a frente de caixa operável na loja física e abrir um marketplace que consome o
mesmo estoque, com cashback por produto e kits.
**Base de código analisada:** branch `malu-note`, migrations até V64, 507 arquivos Java.

> Este documento é opinativo por desenho. Cada decisão traz a recomendação, a alternativa
> descartada e o custo de errar. Onde o código contradiz uma premissa do briefing, o código venceu
> e a divergência está registrada.

---

## 0. Divergências entre o briefing e o código

| # | Briefing | Código | Consequência |
|---|---|---|---|
| 1 | Lista o que falta na `Sale` (customerId, pagamento, desconto, cancelamento) | `SaleRepository` (`core/ports/out/pdv/SaleRepository.java:8-11`) expõe **só `save()`** | Venda registrada é **write-only**: não há `GET /pdv/sales`, não há como reler uma venda pela API. Isso é anterior a qualquer campo faltando |
| 2 | — | `CashRegisterRepository.findOpenByOperator` (`:15`) implementado em `CashRegisterRepositoryImpl.java:34`, **nunca chamado** | Porta morta antecipando PDV-F001. Aproveitar, não recriar |
| 3 | "Como modelar `customerId` sem tornar obrigatório onde não faz sentido" | `Customer` exige `email` **não-nulo e com formato válido** (`core/domain/model/crm/Customer.java:30-35`) | Cadastro rápido no balcão é **impossível hoje** sem inventar e-mail falso. Precede a decisão 5 |
| 4 | `CashRegisterSession` "é um stub sem `open()` nem `close()`" | Além disso, **não tem compact constructor nem `create`/`of`** (`:14-23`) | Diverge da convenção do repositório, não só da funcionalidade |
| 5 | 1269 testes passando | 1259 métodos `@Test` na contagem estática | Compatível com testes parametrizados. **A suíte não foi executada** (sem JDK no ambiente de análise) |
| 6 | — | `SaleEntity` sem `@Version`; `cash_register_sale.warehouse_code` é `VARCHAR` sem FK (V57:14) | Irrelevante hoje (tabela insert-only); vira problema quando o pedido tiver transição de estado. O `warehouse_code` sem FK repete o passivo de EST-C011 |

Tudo o mais que o briefing afirma foi confirmado no código.

---

## 1. Diagnóstico

### 1.1 O que já serve e não deve ser mexido

| Peça | Onde | Por que serve |
|---|---|---|
| Transação única venda + baixa de estoque | `core/service/PdvService.java:39-53` | Saldo insuficiente em qualquer item reverte a venda inteira. É o comportamento certo e o mesmo que o pedido online vai precisar |
| `adjustStock` como **única** porta de escrita de saldo | `core/ports/in/EstoqueUseCase.java:128` / `core/service/EstoqueService.java:187-200` | Compras e PDV já consomem. O marketplace consome a mesma porta e herda validação de SKU, depósito ativo, `@Version` e alerta de reposição sem duplicar regra. Esta é a peça mais valiosa do projeto para o que vem |
| `Pricing` | `core/domain/model/estoque/Pricing.java` | Entrega `marginPercent()` e `marginAmount()` prontos. É a base do dimensionamento de cashback sem escrever matemática nova |
| `StockBalance.@Version` | `StockBalance.java:11`, `StockBalanceEntity` (`@Version`, unique `(sku, warehouse_id)`) | É o que torna a reserva de estoque implementável sem inventar um segundo mecanismo de concorrência |
| Multi-depósito + `WarehouseType` | `Warehouse.java`, `WarehouseType.java` | Existe e funciona — mas ver §2.2: a recomendação é **não** usá-lo para separar canal |
| `PageResult`, `GlobalExceptionHandler`, `AfterCommitExecutor`, schedulers com ShedLock, auditoria por `AuditEvent` | `infra/**` | Infraestrutura transversal inteira reaproveitável. Oito serviços de cleanup agendados já existem como molde para o expirador de reserva |
| Toda a stack de autenticação | `infra/security/**`, `core/domain/model/auth/**` | JWT, refresh rotation, TOTP, verificação de e-mail, reset de senha, OAuth Google, rate limit de login. Reconstruir isso para o cliente de marketplace seria o erro mais caro disponível neste projeto |

### 1.2 O que precisa ser refeito

| Peça | Onde | Problema |
|---|---|---|
| `Sale` / `SaleItem` | `core/domain/model/pdv/Sale.java:12`, `SaleItem.java:8` | Sem cliente, sem pagamento, sem desconto, sem status, sem cancelamento. Não é "faltam campos": é uma entidade que não representa um pedido |
| `SaleItemRequest.unitPrice` | `adapter/in/dtos/request/SaleItemRequest.java:19-21` | Preço **digitado pelo cliente HTTP**. Com `Pricing` existindo (V63), isso deixou de ser simplificação e virou furo: quem tem `PDV_SALE_MANAGE` vende qualquer coisa por qualquer preço, sem trilha de desconto |
| `SaleRepository` | `core/ports/out/pdv/SaleRepository.java` | Write-only. Precisa de `findById`, listagem por sessão e por cliente |
| `CashRegisterSession` | `core/domain/model/pdv/CashRegisterSession.java:14-23` | Record sem invariantes, sem fábricas, sem comportamento. Não há como abrir caixa pelo sistema — hoje exige `INSERT` manual |
| `GET /pdv/sessions` | `adapter/in/controller/PdvController.java:67` | Devolve `PageResult<CashRegisterSession>` — record de domínio na borda HTTP (PDV-C002) |
| `Cart` (ecommerce) | `core/domain/model/ecommerce/Cart.java:11-18` | Record sem comportamento e sem itens. Não há nada a aproveitar; descartar |
| Vínculo operador ↔ sessão | `PdvService.java:40-44` | Valida que a sessão está `OPEN`, **não** que pertence a quem está vendendo |

### 1.3 O que falta inteiro para o marketplace

Não existe no código, em nenhuma forma: `Order`, checkout, pagamento, gateway, webhook, frete,
catálogo público, autenticação de cliente final, cashback, cupom, promoção, kit, reserva de
estoque, documento fiscal. `EcommerceService.listCarts` devolve página vazia com um `// TODO`
(`core/service/EcommerceService.java:20-24`). Financeiro e logística estão no mesmo estado.

`GET /crm/customers/{id}/orders` e `/cashback` retornam `List.of()` no controller
(`adapter/in/controller/CrmController.java:217,230`) e `CustomerResponseDTO.cashback` é constante
zero — são placeholders explícitos, não bugs.

### 1.4 O furo de segurança conhecido, revisitado

`PDV_SALE_MANAGE` movimenta estoque de qualquer SKU em qualquer depósito sem nenhuma permissão
`ESTOQUE_*`, porque `PdvService` chama `EstoqueUseCase` direto e o `@PreAuthorize` só existe na
borda HTTP (`PdvController.java:82`). **Isso está correto e deve continuar assim** — um PDV que
exigisse permissão de estoque para vender seria inoperante. O que precisa mudar é o alcance: com
o ciclo de caixa, a venda passa a ser restrita ao depósito da sessão e ao operador dono da sessão,
que é o controle real. Permissão fina no estoque não é a resposta; escopo na sessão é.

---

## 2. Decisões arquiteturais

### 2.1 Venda de balcão e pedido de marketplace são a mesma entidade

**Recomendação: uma entidade `Order` com discriminador de canal. A `Sale` atual é migrada para ela, não coexiste com ela.**

Tudo que vem depois consulta "vendas, independente de canal": o extrato do cliente
(`GET /crm/customers/{id}/orders`), o ledger de cashback, a devolução (EST-F014), o faturamento do
`financeiro`, o documento fiscal, o relatório de margem. Com duas tabelas, **cada um desses**
consumidores paga um `UNION` ou duplica lógica — e o time é pequeno. Com uma tabela, pagam um
`WHERE channel = ?` quando precisam distinguir, e nada quando não precisam.

O argumento do estado corta ao contrário do que parece: a venda de balcão não é um pedido *sem*
máquina de estados, é um pedido que nasce e termina na mesma transação. Uma máquina de estados
onde o caminho `BALCAO` faz `CRIADO → CONCLUIDO` de uma vez é mais simples do que duas máquinas.

O que **não** vai para dentro de `Order`: sessão de caixa, formas de pagamento, endereço e frete
saem em tabelas próprias, populadas só pelo canal que as tem. Isso evita a `Order` de 40 colunas
onde metade é sempre nula.

**Alternativa descartada:** manter `Sale` e criar `Order` com uma interface comum
(`SalesDocument`) em `core`. Descartada porque a abstração só existe em Java — o banco, que é onde
o relatório e o ledger de fato consultam, continua partido em dois, e nenhuma interface ajuda um
`SELECT`.

**Custo de errar:** unificando e estando errado, paga-se uma coluna nula e dois valores de enum
que o balcão nunca usa — barato e reversível. Separando e estando errado, paga-se `UNION` em todo
relatório, dois caminhos de cashback, dois caminhos de devolução e uma reconciliação entre dois
espaços de id — caro, e pior a cada mês de dado acumulado.

**Consequência imediata:** a migração `cash_register_sale → sales_order` tem que acontecer
**agora**, enquanto o volume é quase zero. Ela é a Fatia 0 do plano.

### 2.2 Baixa imediata no balcão, reserva no checkout — e um só depósito

**Recomendação: PDV continua dando baixa imediata. Marketplace reserva na criação do pedido (checkout), não na entrada no carrinho. Reserva expira em 30 minutos.**

Reservar no carrinho é errado: carrinho abandonado é a regra, não a exceção, e cada um seguraria
estoque por horas. Reservar no checkout é o momento em que o cliente demonstrou intenção real e em
que a janela de pagamento é curta (PIX confirma em segundos).

**Como modelar — reusando o padrão que já existe no estoque:**

O módulo já separa **estado** (`stock_balance`) de **ledger** (`stock_movement`). A reserva segue
exatamente a mesma divisão:

- `stock_balance.reserved_quantity` — nova coluna, **protegida pelo mesmo `@Version`** do saldo.
  Disponível = `quantity - reserved_quantity`, calculado, nunca armazenado.
- `stock_reservation` — linhas de ledger com `order_id`, `expires_at`, `status`, para o
  quem/porquê/até-quando.

Ambos escritos na mesma transação. Isso resolve a interação com o `@Version` da pergunta original
sem inventar nada: duas reservas concorrentes do mesmo SKU disputam a **mesma linha** de
`stock_balance` e uma delas leva 409 `STOCK_UPDATE_CONFLICT` — idêntico a duas movimentações
concorrentes hoje. A invariante `reserved_quantity <= quantity` vira `CHECK` no schema, no mesmo
espírito da V63 ("o domínio é a primeira barreira, mas o schema é a que sobrevive a carga direta").

**Quem libera:** (a) o scheduler de expiração, molde igual aos oito `*CleanupService` existentes,
com `@SchedulerLock`; (b) a confirmação de pagamento, que converte reserva em `SAIDA` real
(`reserved` e `quantity` caem juntos); (c) o cancelamento do pedido.

**Um depósito só, não dois.** O `WarehouseType { LOJA_FISICA, ECOMMERCE }` existe e é tentador usar
para separar os canais, mas para uma tabacaria de uma loja **a prateleira é uma só**. Partir um
estoque físico único em dois pools lógicos gera trabalho permanente de rebalanceamento manual e a
situação absurda de "o site tem 5 e a loja tem 0" com tudo no mesmo armário. A reserva é
precisamente o mecanismo que permite um pool servir dois canais com segurança. O tipo `ECOMMERCE`
fica reservado para quando existir um segundo local físico de verdade; aí EST-F012 (transferência
entre depósitos) passa a fazer sentido.

**Consequência que precisa ser dita:** com um pool só, o `SAIDA` do PDV passa a validar contra o
**disponível**, não contra o físico — senão o balcão vende a unidade que um pedido online pago já
reservou, e o furo aparece na separação. Isso **altera `StockBalance.apply`**, que é um método
maduro e bem testado (`StockBalance.java:50-64`). Alteração cirúrgica, mas com custo de teste real.

**E quando o cliente está no balcão com dinheiro na mão e a última unidade está reservada?** O PDV
recusa com um `errorCode` próprio (`RESERVED_STOCK`), informando quantas unidades estão reservadas
e para qual pedido. O operador cancela o pedido online pelo painel e vende. É um passo a mais, usa
maquinário que precisa existir de qualquer forma (cancelamento) e não exige permissão nova.

**Alternativa descartada:** permissão de override no PDV para furar reserva. Descartada por
complexidade desproporcional — cria um estado onde o pedido online foi silenciosamente
inviabilizado e alguém precisa descobrir isso depois.

**Custo de errar:** sem reserva, overselling em pedido pago — que se paga em estorno, frete
perdido e cliente irritado. Com reserva mal expirada, estoque travado invisível — que se paga em
venda não feita e, pior, é difícil de diagnosticar. Por isso o `stock_reservation` como ledger
consultável importa tanto quanto o contador.

### 2.3 Preço sempre do catálogo; desconto é campo próprio, não preço arbitrário

**Recomendação: `unitPrice` sai do request. O servidor resolve o preço via `EstoqueUseCase.findPricingBySku`. O balcão pode dar desconto, mas como `discountAmount` explícito e sob permissão.**

O item do pedido carrega três coisas congeladas no momento da venda:

- `unit_price` — o `Pricing.effectivePrice()` do catálogo naquele instante;
- `discount_amount` — desconto concedido, `>= 0`, nunca maior que `unit_price * quantity`;
- `cost_price` — **snapshot do custo**.

O snapshot de custo é o item que mais gente esquece e o mais caro de retrofitar: sem ele, a próxima
compra que mudar o `costPrice` do produto reescreve a margem histórica de todos os pedidos
passados. Como o cashback sai da margem, isso não é detalhe contábil — é a base de calibragem do
programa de fidelidade sendo apagada.

Desconto separado do preço (em vez de simplesmente gravar um `unit_price` menor) é o que permite
responder "quanto de margem o desconto do balcão comeu no mês" com um `SUM`, em vez de com
arqueologia.

**Permissão:** `PDV_SALE_DISCOUNT` para qualquer desconto maior que zero. Teto percentual por
pedido em `system_config` (a tabela já existe), default 10%. Acima do teto, 409
`DISCOUNT_LIMIT_EXCEEDED` — não um segundo nível de permissão, que só criaria a tentação de
distribuir a permissão maior.

**Produto sem preço recusa a venda**, com 409 `PRODUCT_NOT_PRICED`. Isso não é escolha nova: o
comentário da V63 já declarou a intenção — *"preço zero não é a mesma coisa que preço desconhecido,
e um DEFAULT 0 aqui faria o PDV vender de graça em vez de recusar a venda de item sem preço"*.

**Vender abaixo do custo continua permitido**, apenas sinalizado — mesma política que
`Pricing.isBelowCost()` já estabeleceu ("queima de estoque e produto-isca são decisões comerciais
legítimas").

**Alternativa descartada:** aceitar `unitPrice` do request quando o chamador tiver permissão
elevada. Descartada porque destrói a rastreabilidade: um preço menor no request é
indistinguível de um erro de digitação, de um desconto autorizado e de uma fraude.

**Custo de errar:** manter o preço vindo do cliente é a decisão mais barata de reverter em código
(é uma linha de DTO) e a mais cara de reverter em dados — todo pedido gravado sem `cost_price` e
sem `discount_amount` é um pedido cuja margem real não é mais recuperável.

**Quebra de contrato:** `POST /pdv/sessions/{id}/sales` muda de forma incompatível. Com dois
endpoints e nenhum consumidor externo, é o momento mais barato que vai existir.

### 2.4 Cashback

#### Taxa: cadeia SKU → categoria → global

Tabela `cashback_rate(id, scope, scope_ref, percent, active, valid_from, valid_to)` com
`scope ∈ {GLOBAL, CATEGORY, SKU}`. Resolução: a regra ativa mais específica vence.

Uma coluna `cashback_percent` em `product` seria mais simples, mas não expressa "categoria carvão
tem 2%" — e `Product.category` hoje é `String` livre, sem tabela própria, então a regra por
categoria precisa ser chaveada pelo texto de qualquer jeito. Uma tabela uniforme para os três
escopos custa o mesmo e evita três mecanismos diferentes.

Taxa **zero é significativa** e diferente de "sem taxa definida": a linha existe com `percent = 0`.
Por isso escopos como linhas, não como coluna anulável.

#### Ledger, nunca saldo mutável

`cashback_entry(id, customer_id, order_id, order_item_id, type, amount, status, available_at, expires_at, created_at)`
com `type ∈ {EARNED, REDEEMED, REVERSED, EXPIRED}`. Saldo é `SUM(amount)` sobre as entradas
liberadas. Nenhuma linha é atualizada depois de escrita; nenhuma é deletada.

Um saldo mutável é impossível de auditar quando o cliente reclama, e reconstruir o extrato depois
do fato não é feito — é adivinhado. Para o volume de uma tabacaria, a soma do ledger nunca vai ser
o gargalo; se um dia for, adiciona-se uma linha de snapshot periódico sem mudar o modelo.

#### Base de cálculo

**Por item, sobre o líquido, cada item com sua taxa:**
`(unit_price * quantity - discount_amount) * cashback_percent / 100`.

A taxa aplicada é **carimbada no `order_item`** (`cashback_percent`). Sem esse snapshot, mudar a
taxa amanhã reescreveria o valor gerado por pedidos de ontem.

#### Carência, expiração e estorno

- **Carência:** `available_at = paid_at + carência`. Default **7 dias**, configurável.
- **Expiração:** `expires_at = available_at + 180 dias`, configurável. Scheduler com `@SchedulerLock`
  escreve entradas `EXPIRED`.
- **Estorno:** cancelamento/devolução escreve `REVERSED` com valor negativo referenciando a entrada
  original.

A regra que faz o caso difícil desaparecer: **a carência tem que ser pelo menos tão longa quanto a
janela de devolução.** Com isso, o cashback de um pedido devolvido ainda está `PENDING` quando o
estorno chega, e nunca houve o que resgatar. Se ainda assim o saldo ficar negativo (devolução fora
da janela), a política é **permitir o negativo** — é uma dívida do cliente, abatida do próximo
ganho — em vez de bloquear a devolução, que puniria a operação por um problema contábil.

#### Resgate

- **Teto por pedido:** percentual do total, default **50%**, em `system_config`.
- **Não se ganha cashback sobre a parte paga com cashback.** A base de cálculo do `EARNED` desconta
  o valor resgatado. Sem essa regra, o programa é uma máquina de imprimir crédito.
- Resgate **não é forma de pagamento**: é uma entrada `REDEEMED` no ledger de pontos mais um
  desconto no pedido. Misturar com `order_payment` faria o DRE contar a mesma receita duas vezes.

#### Impacto na margem — com os números do `Pricing` que já existe

Usando `marginPercent()` e `marginAmount()` (`Pricing.java:117-142`):

| Item | Custo | Venda | Margem R$ | Margem % | Cashback 8% | % da margem consumido |
|---|---|---|---|---|---|---|
| Carvão | 18,00 | 22,00 | 4,00 | 18,2% | 1,76 | **44,0%** |
| Descartável | 2,00 | 3,50 | 1,50 | 42,9% | 0,28 | 18,7% |
| Narguilé médio | 180,00 | 320,00 | 140,00 | 43,8% | 25,60 | 18,3% |
| Essência artesanal | 25,00 | 75,00 | 50,00 | 66,7% | 6,00 | **12,0%** |

A intuição do dono está certa e o número confirma: 8% em carvão come 44% do lucro do item, e a
mesma taxa em essência artesanal custa 12%.

**Regra prática recomendada — cashback ≈ 15% da margem:**

```
taxa_sugerida ≈ marginPercent × 0,15
```

| Item | Margem % | Taxa sugerida |
|---|---|---|
| Carvão | 18,2% | **2,5%** |
| Descartável | 42,9% | 6,5% |
| Narguilé médio | 43,8% | 6,5% |
| Essência artesanal | 66,7% | **10%** |

Com essa distribuição, o custo total do programa fica perto de 15% da margem bruta em vez dos ~30%
que uma taxa fixa de 8% produziria numa cesta média — sem que o cliente perceba estar recebendo
menos, porque o que ele compara é o valor absoluto do item caro.

**Isso não precisa de matemática nova:** `Pricing.marginPercent()` já entrega o insumo. Um endpoint
de diagnóstico — *"produtos cuja taxa vigente consome mais de X% da margem"* — é uma consulta, e
deve entrar junto com o cadastro de taxas, senão ninguém vai descobrir o carvão a 8% antes do
fechamento do mês.

**Alternativa descartada:** cashback sobre o total do pedido com uma taxa média. Descartada porque
é exatamente o que o dono já identificou como problema, e porque impede qualquer análise por item.

**Custo de errar:** o ledger é o ponto de não-retorno. Nascendo como coluna de saldo, o extrato do
cliente nunca mais existe. Os parâmetros (taxas, carência, teto) são todos configuráveis e
reversíveis — errar neles custa um mês de margem, não o modelo.

### 2.5 Cliente: opcional no balcão, obrigatório no marketplace, garantido pelo schema

**Recomendação: `customer_id` anulável em `sales_order`, com a obrigatoriedade condicionada ao canal por `CHECK`.**

```sql
CHECK (channel = 'BALCAO' OR customer_id IS NOT NULL)
```

O compact constructor do record espelha a mesma regra. É exatamente o padrão que a V63 declarou:
o domínio é a primeira barreira, o schema é a que sobrevive a carga direta e script de correção.

**Bloqueador real, descoberto no código:** `Customer` exige `email` não-nulo e com formato válido
(`Customer.java:30-35`). O cliente de balcão que quer cashback tem CPF e telefone, e frequentemente
não quer dar e-mail. Hoje, identificá-lo exige inventar um e-mail — que polui a base, quebra o
disparo de campanha (`CampaignAutomation`) e destrói a unicidade por e-mail.

**Consequência:** antes de haver cashback no balcão, o CRM precisa de uma correção
(`CRM-Cxxx`) tornando `email` opcional, com a unicidade passando a ser por CPF quando o e-mail
faltar. Isso é uma alteração numa invariante de domínio já testada — tem custo, e está na Fatia 2.

**Fluxo no balcão:** `GET /crm/customers/lookup?cpf=` ou `?contato=` devolve o cliente;
não achando, um cadastro rápido com nome + contato + CPF. Sem cliente, sem cashback — e é esse o
incentivo que faz o operador perguntar "CPF na nota?" sem precisar de treinamento.

**Custo de errar:** deixar `customer_id` obrigatório mataria a venda anônima de passagem, que é a
maioria do balcão. Deixá-lo opcional sem o `CHECK` abre a porta para pedido online órfão — e um
pedido online sem cliente não tem para quem entregar nem para quem estornar.

### 2.6 Pagamento: uma tabela, dois fluxos

**Recomendação: `order_payment(id, order_id, method, amount, status, gateway_ref, authorized_at, captured_at, created_at)`. Várias linhas por pedido = pagamento dividido.**

| | Balcão | Online |
|---|---|---|
| Criação | já em `CAPTURED` — o dinheiro está na gaveta | `PENDING` com `gateway_ref` |
| Confirmação | síncrona | webhook do gateway |
| Divisão | R$50 dinheiro + R$30 débito = duas linhas | normalmente uma linha |
| Estorno | anulação na sessão de caixa | chamada de estorno no gateway |

**Troco não é linha de pagamento.** É `change_amount` no pedido, derivado de
`soma dos pagamentos em DINHEIRO − total do pedido` quando positivo. Modelar troco como pagamento
negativo faz todo `SUM(amount)` mentir.

**Invariante:** o pedido só vai a `PAGO` quando
`soma dos pagamentos CAPTURED >= total − cashback resgatado`.

Uma abstração só, porque o que muda entre os canais é **quem confirma** (o operador ou o webhook),
não o que é registrado. Dois modelos obrigariam o fechamento de caixa, o DRE e o estorno a
conhecerem os dois.

**Port de gateway:** `PaymentGatewayPort` em `core/ports/out/ecommerce` — o `package-info` do pacote
já antecipa exatamente isso. Um port, um adapter. Para tabacaria brasileira, PIX via Mercado Pago
é a escolha pragmática (taxa baixa, confirmação em segundos, sem antecipação de recebível).

**O webhook é a superfície mais perigosa do projeto:** público, precisa validar assinatura HMAC,
precisa ser **idempotente por `gateway_ref`** (gateways reenviam), e não pode confiar em nenhum
valor do corpo — o valor pago se confirma consultando o gateway, nunca lendo o payload.

**Custo de errar:** duas abstrações custam duplicação em três consumidores. Uma abstração mal feita
— troco como pagamento, ou cashback como forma de pagamento — custa relatório financeiro errado,
que só é descoberto quando não bate com o extrato bancário.

### 2.7 Ciclo de caixa

**Recomendação: espelhar o `StockCount`, que já resolveu o mesmo problema.**

O balanço de inventário (`EstoqueService.closeStockCount`) confronta contado × esperado, carimba a
divergência e **fecha mesmo assim**. O fechamento de caixa é o mesmo problema com dinheiro. Reusar
o vocabulário e o comportamento poupa decisão e faz o sistema ser previsível para quem opera.

- `open(operator, openingAmount)` → `OPEN`. **Uma sessão aberta por operador**, garantida por índice
  parcial único: `CREATE UNIQUE INDEX ... ON cash_register_session(operator) WHERE status = 'OPEN'`.
  `findOpenByOperator` já existe na porta (`CashRegisterRepository.java:15`) e finalmente passa a
  ser chamado.
- `cash_movement(id, session_id, type, amount, reason, username, created_at)` com
  `type ∈ {SANGRIA, SUPRIMENTO}`.
- `close(countedAmount)` calcula
  `expected = openingAmount + vendas em DINHEIRO − sangrias + suprimentos`, grava `counted` e
  `difference`. **Divergência não bloqueia o fechamento** — é registrada, exatamente como no
  balanço de inventário.
- O fechamento reporta os totais **por forma de pagamento** separadamente. Só `DINHEIRO` entra na
  conferência da gaveta; cartão e PIX vão para conferência contra a adquirente.
- `registerSale` passa a exigir que a sessão pertença a quem está vendendo, e o depósito da venda
  passa a vir da sessão em vez do request. Isso fecha o buraco de isolamento que o próprio README
  do módulo documenta (`docs/dominios/vendas-balcao/README.md:72-75`).

**Custo de errar:** sem ciclo de caixa, não há conferência — e sem conferência não há como
distinguir erro de troco de desvio. É a lacuna que impede a loja de confiar no sistema.

### 2.8 Fiscal: integrar, nunca construir — e não agora

**Recomendação: NFC-e via emissor terceiro (Focus NFe ou PlugNotas). Não é bloqueante para o primeiro release interno; é bloqueante para aposentar o processo fiscal atual.**

Construir emissão própria significa: schemas XML por UF, assinatura com certificado A1/A3,
contingência offline, janela de cancelamento, eventos de manifestação, homologação na SEFAZ, e
manutenção perpétua a cada mudança de layout. É um projeto de meses. Um emissor terceiro entrega
isso por ordem de R$100–300/mês.

Para tabacaria especificamente, o argumento é ainda mais forte: fumo e derivados têm **ICMS-ST**
(imposto recolhido antes, pelo fabricante) e IPI. A nota do varejista sai com CST 60 e NCM/CEST
específicos por produto. Errar essa configuração não gera um bug — gera autuação. É precisamente o
tipo de coisa que o emissor terceiro trata como configuração por produto.

**Onde entra na ordem:** Fatia 11, depois do PDV operar. A justificativa honesta é que a loja hoje
já emite nota por algum outro meio; o PDV pode rodar em paralelo a esse processo enquanto a
operação amadurece, e a integração NFC-e é o portão para desligar o processo antigo — não para
ligar o PDV.

**O que precisa ser decidido agora, mesmo sem construir nada:** o pedido tem que ser **imutável
depois de concluído** e ter **numeração estável e sem buracos**. Adicionar NCM/CEST/CFOP ao produto
depois é uma migration trivial. Retrofitar imutabilidade e numeração num modelo de pedido já em
produção não é.

**Custo de errar:** construir emissão própria é o erro mais caro do documento inteiro — consome o
time todo por meses e entrega algo pior do que o produto de prateleira. Adiar a integração é
barato **desde que** o pedido nasça imutável e numerado.

### 2.9 Cliente do marketplace: mesmo `UserEntity`, `ROLE_CUSTOMER`, ligado ao `Customer`

**Recomendação: reaproveitar `UserEntity` com uma role nova, ligado 1:1 ao `Customer` do CRM por `users.customer_id`.**

O que existe e teria de ser reconstruído numa entidade separada: hash de senha, verificação de
e-mail, reset de senha, TOTP, rotação de refresh token, detecção de roubo de token, sessões
listáveis/revogáveis, rate limit de login, OAuth Google. São milhares de linhas testadas. Duplicar
isso para o cliente final é o caminho mais curto para um bug de autenticação — e bug de
autenticação em superfície pública é incidente, não defeito.

O RBAC é por permissão granular, então `ROLE_CUSTOMER` é simplesmente uma role com um conjunto
minúsculo (`SHOP_CART_OWN`, `SHOP_ORDER_OWN`, `SHOP_CASHBACK_OWN`). Nenhuma permissão de operador
chega perto dela.

**O risco real não é a role — é a autorização por linha.** `@PreAuthorize` não sabe dizer "só os
seus pedidos". Daí a regra dura:

> **O marketplace nunca aceita `customerId` vindo do request.** O `customerId` é sempre resolvido a
> partir do principal autenticado, dentro do service. Um endpoint `/shop/orders/{id}` valida
> propriedade antes de responder, e responde **404, não 403**, para pedido de outro cliente — 403
> confirma a existência do recurso.

**Impactos concretos:**

- **JWT:** claim `customerId` para evitar um lookup por request. Precisa ser validado como qualquer
  claim, não confiado.
- **Superfície pública:** um novo ramo `/shop/**` com regras próprias em `SecurityConfig`. Público:
  catálogo, registro, login. Autenticado como cliente: carrinho, checkout, meus pedidos, meu
  cashback.
- **`anyRequest().authenticated()`** (`SecurityConfig.java:117`) significa que qualquer endpoint sem
  `@PreAuthorize` explícito passa a ser alcançável por um cliente autenticado. Hoje isso é inócuo
  porque todos têm. Vale transformar em garantia: **um teste ArchUnit exigindo `@PreAuthorize` em
  todo método de controller fora de `/auth` e `/shop`.** É barato e fecha uma classe inteira de
  regressão futura.
- **Rate limiting:** `LoginRateLimitingFilter` cobre só login. Catálogo público e, sobretudo,
  **registro** (que cria linhas) precisam de limite. PLAT-C030 já rastreia a lacuna geral.
- **Colisão de `username`:** cliente que se auto-cadastra vai colidir com username de operador.
  Recomendação: cliente registra com e-mail como username, e o `UserEntity` ganha um discriminador
  `user_type ∈ {OPERATOR, CUSTOMER}` com unicidade por `(user_type, username)`.

**Alternativa descartada:** entidade `CustomerAccount` separada com fluxo próprio. Descartada pelo
custo de reconstrução e pelo risco de segurança, não por elegância — a separação seria de fato mais
limpa conceitualmente.

**Custo de errar:** reaproveitar e estar errado custa um discriminador e algumas regras de
`SecurityConfig`. Separar e estar errado custa uma segunda implementação de autenticação para
manter em paralelo pelo resto da vida do projeto. E errar a checagem de propriedade custa vazamento
de dado de cliente — o único item deste documento que é **irreversível**.

### 2.10 Kits: virtuais, de um nível só

**Recomendação: kit virtual — explode na venda e dá baixa nos componentes. Sem saldo próprio.**

O dono quer vender componentes separadamente, e é isso que decide: um kit físico tem saldo próprio,
o que significa que cada kit montado retira componentes da venda avulsa, e você chega no absurdo de
"5 kits na prateleira" com o cliente que quer só o carvão vendo saldo zero. Um pool só, kit
derivado.

**Modelo:**

- `product.type ∈ {SIMPLES, KIT}`, nova coluna, default `SIMPLES`.
- `product_kit_component(id, kit_sku, component_sku, quantity)`, `UNIQUE(kit_sku, component_sku)`.

**Ciclos: eliminados por construção, não detectados por travessia.** Componente **precisa ser
`SIMPLES`** — kit dentro de kit é proibido. Um nível é suficiente para uma tabacaria, a regra é
verificável com uma consulta em vez de um algoritmo de grafo, e ela mata a classe inteira de bugs
de recursão (ciclo, profundidade, explosão exponencial de saldo derivado). É restrição deliberada,
e deve estar documentada como tal.

**Saldo derivado:** `min(floor(saldo_componente / quantidade_na_receita))` sobre os componentes. Um
KIT **nunca tem linha em `stock_balance`**. `GET /estoque/stock-balance` passa a resolver os dois
casos.

**Preço:** o kit tem `Pricing` próprio no seu SKU — é o ponto do kit ser mais barato que a soma. Mas
o **custo é derivado**: `findPricingBySku` para um KIT devolve
`Pricing.of(soma dos custos dos componentes, null, salePrice do kit)`. Com isso, `marginPercent()`,
`marginAmount()` e `isBelowCost()` funcionam corretamente para kits **sem uma linha de matemática
nova** — o encaixe com o `Pricing` de V63 é limpo.

**Consequência na venda:** o `order_item` guarda o **SKU do kit** (é o que o cliente comprou e o
preço que ele pagou), mas as movimentações de estoque são nos componentes. Item de pedido e
movimentação deixam de ser 1:1. O `stock_movement.reason` precisa carregar o pedido **e** o SKU do
kit de origem, senão a trilha não é reconstruível.

**Cashback do kit:** taxa do SKU do kit, resolvida pela mesma cadeia. Não tentar compor a média dos
componentes — a taxa do kit é uma decisão comercial própria.

**Custo de errar:** kit físico é caro de reverter porque cria saldo real que precisa ser
desmontado. Kit aninhado é caro porque contamina toda consulta de saldo com recursão. Kit virtual
de um nível é a escolha que dá para expandir depois sem migrar dado.

---

## 3. Modelo de domínio proposto

Records imutáveis, compact constructor validando invariantes, par `create`/`of`, mutação por cópia
— convenções de `core/domain/model/estoque`.

### 3.1 Pacote `core/domain/model/pedido`

```java
public record Order(
    Long id,
    String orderNumber,        // numeração fiscal estável, emitida na conclusão
    SalesChannel channel,      // BALCAO | MARKETPLACE
    OrderStatus status,
    Long customerId,           // null só em BALCAO
    Long sessionId,            // null em MARKETPLACE
    String warehouseCode,
    List<OrderItem> items,
    BigDecimal grossAmount,    // Σ unitPrice * quantity
    BigDecimal discountAmount, // Σ discount dos itens + desconto de cabeçalho
    BigDecimal cashbackRedeemed,
    BigDecimal netAmount,      // gross - discount - cashbackRedeemed
    BigDecimal changeAmount,   // troco, só BALCAO
    Instant createdAt,
    Instant paidAt,
    Instant concludedAt,
    Instant cancelledAt,
    long version
)
```

**Invariantes** (compact constructor):
- `channel`, `status`, `warehouseCode` obrigatórios; `items` não vazio.
- `channel == MARKETPLACE → customerId != null`.
- `channel == BALCAO → sessionId != null`.
- `channel == MARKETPLACE → sessionId == null`.
- Nenhum dos valores monetários negativo; `netAmount == gross − discount − cashbackRedeemed`.
- `changeAmount > 0` só em `BALCAO`.
- `status == CANCELADO ↔ cancelledAt != null`.

**Fábricas:** `Order.openBalcao(sessionId, warehouseCode, customerId, items)`,
`Order.openMarketplace(customerId, warehouseCode, items)`, `Order.of(...)`.
**Transições por cópia:** `withPayment(...)`, `paid(Instant)`, `concluded(Instant)`,
`cancelled(Instant, reason)`, `withCashbackRedeemed(BigDecimal)`.

```java
public record OrderItem(
    Long id, String sku, BigDecimal quantity,
    BigDecimal unitPrice,      // congelado do catálogo
    BigDecimal costPrice,      // snapshot — a margem histórica depende dele
    BigDecimal discountAmount,
    BigDecimal cashbackPercent // taxa vigente, carimbada
) {
    BigDecimal grossAmount();  // quantity * unitPrice
    BigDecimal netAmount();    // gross - discount
    BigDecimal cashbackAmount();
    BigDecimal marginAmount(); // net - costPrice * quantity
}
```

`unitPrice`, `costPrice` e `cashbackPercent` podem ser nulos apenas na reconstituição de pedidos
anteriores à migração; para pedidos novos, `unitPrice` é obrigatório e
`0 <= discountAmount <= grossAmount()`.

### 3.2 Estados e transições

```
                    ┌──────────────── BALCAO ────────────────┐
                    │  (criação e conclusão na mesma trx)    │
   CRIADO ──────────┴──────────────────────────────► CONCLUIDO
      │                                                  │
      │  MARKETPLACE                                     │ devolução
      ▼                                                  ▼
  AGUARDANDO_PAGAMENTO ──pagamento──► PAGO ──► SEPARADO ──► ENVIADO ──► ENTREGUE
      │                                │           │           │           │
      │ expiração / cancelamento       │           │           │           │
      ▼                                ▼           ▼           ▼           ▼
                              CANCELADO ◄──────────┴───────────┴───────────┘
                                                        (com estorno)
```

| De | Para | Quem dispara | Efeito colateral |
|---|---|---|---|
| — | `CRIADO` | PDV | reserva nenhuma; baixa `SAIDA` imediata |
| `CRIADO` | `CONCLUIDO` | PDV, mesma transação | cashback `EARNED` `PENDING`; pagamentos `CAPTURED` |
| — | `AGUARDANDO_PAGAMENTO` | checkout | cria `stock_reservation` com `expires_at` |
| `AGUARDANDO_PAGAMENTO` | `PAGO` | webhook | reserva → `SAIDA`; cashback `EARNED` `PENDING` |
| `AGUARDANDO_PAGAMENTO` | `CANCELADO` | scheduler / cliente | libera reserva |
| `PAGO` | `SEPARADO` → `ENVIADO` → `ENTREGUE` | operação | nenhum em estoque |
| `PAGO`+ | `CANCELADO` | operador | `ENTRADA` estornando; cashback `REVERSED`; estorno no gateway |
| `CONCLUIDO` | `CANCELADO` | operador | idem, mais anulação na sessão de caixa |

`CANCELADO` e `ENTREGUE` são terminais. `CONCLUIDO` é terminal para o balcão exceto por devolução.

### 3.3 Pacote `core/domain/model/pdv` (revisado)

```java
public record CashRegisterSession(
    Long id, String operator, Instant openedAt, BigDecimal openingAmount,
    Instant closedAt, String closedBy,
    BigDecimal expectedAmount, BigDecimal countedAmount, BigDecimal differenceAmount,
    String warehouseCode, Status status
) {
    public enum Status { OPEN, CLOSED }
    public static CashRegisterSession open(String operator, BigDecimal openingAmount, String warehouseCode);
    public static CashRegisterSession of(...);
    public CashRegisterSession closedWith(BigDecimal expected, BigDecimal counted, String closedBy);
    public boolean isOpen();
    public boolean diverges();   // mesma semântica de StockCountItem.diverges()
}
```

Invariantes: `operator`, `warehouseCode`, `status` obrigatórios; `openingAmount >= 0`;
`status == CLOSED ↔ closedAt != null && countedAmount != null`;
`differenceAmount == countedAmount − expectedAmount` (pode ser negativo — falta no caixa é um
número legítimo).

```java
public record CashMovement(Long id, Long sessionId, CashMovementType type,
        BigDecimal amount, String reason, String username, Instant createdAt) {
    public enum CashMovementType { SANGRIA, SUPRIMENTO }
}
```
`amount > 0` sempre; o sinal vem do `type`, não do valor.

### 3.4 Pacote `core/domain/model/pagamento`

```java
public record OrderPayment(Long id, Long orderId, PaymentMethod method, BigDecimal amount,
        PaymentStatus status, String gatewayRef, Instant authorizedAt, Instant capturedAt,
        Instant createdAt) {
    public enum PaymentMethod { DINHEIRO, DEBITO, CREDITO, PIX, GATEWAY_PIX, GATEWAY_CARTAO }
    public enum PaymentStatus { PENDING, AUTHORIZED, CAPTURED, REFUNDED, FAILED }
}
```
`amount > 0`; `status == CAPTURED → capturedAt != null`; método de gateway exige `gatewayRef`.

### 3.5 Pacote `core/domain/model/cashback`

```java
public record CashbackRate(Long id, RateScope scope, String scopeRef, BigDecimal percent,
        boolean active, Instant validFrom, Instant validTo) {
    public enum RateScope { GLOBAL, CATEGORY, SKU }
    public boolean appliesAt(Instant moment);
}
```
`0 <= percent <= 100`; `scope == GLOBAL ↔ scopeRef == null`; `validTo == null || validTo > validFrom`.

```java
public record CashbackEntry(Long id, Long customerId, Long orderId, Long orderItemId,
        EntryType type, BigDecimal amount, EntryStatus status,
        Instant availableAt, Instant expiresAt, Instant createdAt, Long reversesEntryId) {
    public enum EntryType { EARNED, REDEEMED, REVERSED, EXPIRED }
    public enum EntryStatus { PENDING, AVAILABLE, CONSUMED, EXPIRED, REVERSED }
}
```
`EARNED` tem `amount > 0`; `REDEEMED`, `REVERSED` e `EXPIRED` têm `amount < 0`. `REVERSED` exige
`reversesEntryId`. Nenhum método de mutação — o ledger só recebe linhas novas.

### 3.6 Pacote `core/domain/model/estoque` (acréscimos)

```java
public record StockReservation(Long id, String sku, Long warehouseId, BigDecimal quantity,
        Long orderId, ReservationStatus status, Instant expiresAt, Instant createdAt) {
    public enum ReservationStatus { ACTIVE, CONSUMED, RELEASED, EXPIRED }
    public boolean isExpiredAt(Instant moment);
}
```

`StockBalance` ganha `reservedQuantity` com invariantes
`reservedQuantity >= 0` e `reservedQuantity <= quantity`, mais:

```java
public BigDecimal availableQuantity();                    // quantity - reservedQuantity
public StockBalance reserve(BigDecimal qty);              // InsufficientStockException se > available
public StockBalance releaseReservation(BigDecimal qty);
public StockBalance consumeReservation(BigDecimal qty);   // quantity e reserved caem juntos
```

`apply(SAIDA, qty)` passa a validar contra `availableQuantity()`.
`ProductType { SIMPLES, KIT }` entra em `Product`; `KitComponent(Long id, String kitSku, String componentSku, BigDecimal quantity)` é novo.

---

## 4. Schema e migrations (a partir de V65)

Regras seguidas: sequencial, nunca editar migration aplicada, `ON CONFLICT DO NOTHING` em toda
inserção de permissão, `CHECK` espelhando as invariantes do compact constructor, `COMMENT` nas
colunas cuja nulidade não é óbvia. **Toda permissão nova entra também em `SeedConfig` e
`DevRoleBootstrapConfig`** — esquecer isso gera 403 em dev, e já aconteceu duas vezes.

### V65 — `sales_order` / `order_item` (renomeação + campos)

```sql
ALTER TABLE cash_register_sale RENAME TO sales_order;
ALTER TABLE sale_item          RENAME TO order_item;
ALTER TABLE order_item RENAME COLUMN sale_id TO order_id;

ALTER TABLE sales_order ADD COLUMN channel           VARCHAR(20);
ALTER TABLE sales_order ADD COLUMN status            VARCHAR(30);
ALTER TABLE sales_order ADD COLUMN order_number      VARCHAR(30);
ALTER TABLE sales_order ADD COLUMN customer_id       BIGINT REFERENCES customer(id);
ALTER TABLE sales_order ADD COLUMN discount_amount   NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE sales_order ADD COLUMN cashback_redeemed NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE sales_order ADD COLUMN net_amount        NUMERIC(14,2);
ALTER TABLE sales_order ADD COLUMN change_amount     NUMERIC(14,2);
ALTER TABLE sales_order ADD COLUMN paid_at           TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN concluded_at      TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN cancelled_at      TIMESTAMP;
ALTER TABLE sales_order ADD COLUMN cancel_reason     VARCHAR(255);
ALTER TABLE sales_order ADD COLUMN version           BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sales_order ALTER COLUMN session_id DROP NOT NULL;   -- marketplace não tem caixa

UPDATE sales_order SET channel = 'BALCAO', status = 'CONCLUIDO',
       net_amount = total_amount, concluded_at = sold_at,
       order_number = 'LEG-' || LPAD(id::text, 8, '0');
ALTER TABLE sales_order ALTER COLUMN channel      SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN status       SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN net_amount   SET NOT NULL;
ALTER TABLE sales_order ALTER COLUMN order_number SET NOT NULL;
ALTER TABLE sales_order ADD CONSTRAINT uk_sales_order_number UNIQUE (order_number);

ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_customer_by_channel
    CHECK (channel = 'BALCAO' OR customer_id IS NOT NULL);
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_session_by_channel
    CHECK ((channel = 'BALCAO' AND session_id IS NOT NULL)
        OR (channel = 'MARKETPLACE' AND session_id IS NULL));
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_amounts_non_negative
    CHECK (total_amount >= 0 AND discount_amount >= 0
       AND cashback_redeemed >= 0 AND net_amount >= 0);
ALTER TABLE sales_order ADD CONSTRAINT ck_sales_order_cancelled_consistency
    CHECK ((status = 'CANCELADO') = (cancelled_at IS NOT NULL));

ALTER TABLE order_item ADD COLUMN cost_price       NUMERIC(14,2);
ALTER TABLE order_item ADD COLUMN discount_amount  NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE order_item ADD COLUMN cashback_percent NUMERIC(9,4);
ALTER TABLE order_item ADD CONSTRAINT ck_order_item_discount_within_gross
    CHECK (discount_amount >= 0 AND discount_amount <= quantity * unit_price);

CREATE INDEX idx_sales_order_customer_id ON sales_order (customer_id);
CREATE INDEX idx_sales_order_channel_status ON sales_order (channel, status);
CREATE INDEX idx_sales_order_concluded_at ON sales_order (concluded_at DESC);
CREATE SEQUENCE order_number_seq START 1;
```

Nulidade deliberada: `customer_id` (venda anônima de balcão é o caso normal, e o `CHECK` amarra o
marketplace); `cost_price` e `cashback_percent` em `order_item` (pedidos legados não têm o
snapshot, e um default zero mentiria sobre a margem — mesma razão da V63 para `cost_price` em
`product`); `paid_at`/`concluded_at`/`cancelled_at` (marcam eventos que podem não ter ocorrido);
`change_amount` (só faz sentido em dinheiro no balcão).

`escala 4` em `cashback_percent` pela mesma razão da V63: é input de fórmula, não valor de
exibição.

### V66 — ciclo de caixa

```sql
ALTER TABLE cash_register_session ADD COLUMN warehouse_code    VARCHAR(50);
ALTER TABLE cash_register_session ADD COLUMN closed_by         VARCHAR(80);
ALTER TABLE cash_register_session ADD COLUMN expected_amount   NUMERIC(14,2);
ALTER TABLE cash_register_session ADD COLUMN counted_amount    NUMERIC(14,2);
ALTER TABLE cash_register_session ADD COLUMN difference_amount NUMERIC(14,2);

UPDATE cash_register_session SET warehouse_code =
    (SELECT code FROM warehouse WHERE type = 'LOJA_FISICA' ORDER BY id LIMIT 1)
    WHERE warehouse_code IS NULL;
ALTER TABLE cash_register_session ALTER COLUMN warehouse_code SET NOT NULL;

-- Uma sessão aberta por operador. Índice parcial: o banco garante o que o domínio promete.
CREATE UNIQUE INDEX uk_cash_register_session_open_operator
    ON cash_register_session (operator) WHERE status = 'OPEN';

ALTER TABLE cash_register_session ADD CONSTRAINT ck_cash_register_session_closed_consistency
    CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL AND counted_amount IS NOT NULL));

CREATE TABLE cash_movement (
    id         BIGSERIAL     PRIMARY KEY,
    session_id BIGINT        NOT NULL REFERENCES cash_register_session(id) ON DELETE CASCADE,
    type       VARCHAR(20)   NOT NULL,
    amount     NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    reason     VARCHAR(255)  NOT NULL,
    username   VARCHAR(80)   NOT NULL,
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT ck_cash_movement_type CHECK (type IN ('SANGRIA','SUPRIMENTO'))
);
CREATE INDEX idx_cash_movement_session_id ON cash_movement (session_id);
```
Permissões: `PDV_SESSION_MANAGE`, `PDV_SESSION_CLOSE`, `PDV_SALE_DISCOUNT`, `PDV_SALE_CANCEL`.

### V67 — cliente identificável no balcão (correção CRM)

```sql
ALTER TABLE customer ALTER COLUMN email DROP NOT NULL;
CREATE UNIQUE INDEX uk_customer_cpf ON customer (cpf) WHERE cpf IS NOT NULL;
ALTER TABLE customer ADD CONSTRAINT ck_customer_has_identifier
    CHECK (email IS NOT NULL OR cpf IS NOT NULL);
```
Permissão: `CRM_CUSTOMER_LOOKUP` (consulta pontual por CPF/contato, separada de
`CRM_CUSTOMER_READ` — o operador de balcão precisa achar um cliente, não listar a base inteira).

### V68 — pagamentos

```sql
CREATE TABLE order_payment (
    id            BIGSERIAL     PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    method        VARCHAR(30)   NOT NULL,
    amount        NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    status        VARCHAR(20)   NOT NULL,
    gateway_ref   VARCHAR(120),
    authorized_at TIMESTAMP,
    captured_at   TIMESTAMP,
    created_at    TIMESTAMP     NOT NULL,
    CONSTRAINT ck_order_payment_captured CHECK ((status = 'CAPTURED') = (captured_at IS NOT NULL))
);
CREATE INDEX idx_order_payment_order_id ON order_payment (order_id);
-- Idempotência do webhook: o gateway reenvia, e reenvio não pode virar segundo pagamento.
CREATE UNIQUE INDEX uk_order_payment_gateway_ref
    ON order_payment (gateway_ref) WHERE gateway_ref IS NOT NULL;
```
Permissões: `PDV_PAYMENT_MANAGE`, `FIN_PAYMENT_READ`.

### V69 — cashback

```sql
CREATE TABLE cashback_rate (
    id         BIGSERIAL     PRIMARY KEY,
    scope      VARCHAR(20)   NOT NULL,
    scope_ref  VARCHAR(80),
    percent    NUMERIC(9,4)  NOT NULL CHECK (percent >= 0 AND percent <= 100),
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    valid_from TIMESTAMP     NOT NULL,
    valid_to   TIMESTAMP,
    CONSTRAINT ck_cashback_rate_scope CHECK (scope IN ('GLOBAL','CATEGORY','SKU')),
    CONSTRAINT ck_cashback_rate_scope_ref CHECK ((scope = 'GLOBAL') = (scope_ref IS NULL)),
    CONSTRAINT ck_cashback_rate_validity  CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uk_cashback_rate_active_scope
    ON cashback_rate (scope, COALESCE(scope_ref, '')) WHERE active AND valid_to IS NULL;

CREATE TABLE cashback_entry (
    id                BIGSERIAL     PRIMARY KEY,
    customer_id       BIGINT        NOT NULL REFERENCES customer(id),
    order_id          BIGINT        REFERENCES sales_order(id),
    order_item_id     BIGINT        REFERENCES order_item(id),
    type              VARCHAR(20)   NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    available_at      TIMESTAMP,
    expires_at        TIMESTAMP,
    reverses_entry_id BIGINT        REFERENCES cashback_entry(id),
    created_at        TIMESTAMP     NOT NULL,
    CONSTRAINT ck_cashback_entry_sign
        CHECK ((type = 'EARNED' AND amount > 0) OR (type <> 'EARNED' AND amount < 0)),
    CONSTRAINT ck_cashback_entry_reversal
        CHECK ((type = 'REVERSED') = (reverses_entry_id IS NOT NULL))
);
CREATE INDEX idx_cashback_entry_customer_status ON cashback_entry (customer_id, status);
CREATE INDEX idx_cashback_entry_pending_release ON cashback_entry (available_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_cashback_entry_expiring ON cashback_entry (expires_at)
    WHERE status = 'AVAILABLE';

INSERT INTO cashback_rate (scope, scope_ref, percent, active, valid_from)
VALUES ('GLOBAL', NULL, 8.0000, TRUE, NOW());
```
`order_id`/`order_item_id` anuláveis porque `EXPIRED` e ajustes manuais não têm pedido de origem.
Permissões: `CASHBACK_RATE_MANAGE`, `CASHBACK_READ`, `CASHBACK_REDEEM`, `CASHBACK_ADJUST`.

### ~~V69~~ — reserva de estoque: **já aplicada em V64**

A reserva saiu na frente do plano. `V64__estoque_stock_reservation.sql` já está no working tree e
implementa o desenho de §2.2: `stock_balance.reserved_quantity` sob o `@Version` existente (é isso
que faz duas reservas concorrentes colidirem em 409 em vez de corromperem o disponível), o ledger
`stock_reservation` com índice parcial de varredura, e as permissões `ESTOQUE_RESERVATION_READ` /
`ESTOQUE_RESERVATION_MANAGE`.

**Uma divergência deliberada em relação ao que esta seção previa:** o dono da reserva é
`owner_reference VARCHAR(80)`, texto livre, e **não** `order_id BIGINT REFERENCES sales_order(id)`.
Está certo — a tabela de pedido ainda não existe, e FK para tabela inexistente seria coluna morta.
O `COMMENT` da coluna já registra a intenção: passa a ser `ORDER:{id}` quando o domínio de pedido
existir. **Item de atenção da Fatia 9:** o checkout precisa usar exatamente esse formato, e vale
avaliar promover a coluna a FK quando `sales_order` existir.

O que **ainda falta** da Fatia 7, e continua valendo: endpoints em `EstoqueController`, o scheduler
de expiração chamando `expireReservations` (o método existe em `EstoqueService`; o job com
`@SchedulerLock` não), e a suíte de testes. Ver o roteiro em
[`dominios/estoque/proximos-passos.md`](dominios/estoque/proximos-passos.md).

### V70 — kits

```sql
ALTER TABLE product ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'SIMPLES';
ALTER TABLE product ADD CONSTRAINT ck_product_type CHECK (type IN ('SIMPLES','KIT'));

CREATE TABLE product_kit_component (
    id            BIGSERIAL     PRIMARY KEY,
    kit_sku       VARCHAR(50)   NOT NULL,
    component_sku VARCHAR(50)   NOT NULL,
    quantity      NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_kit_component UNIQUE (kit_sku, component_sku),
    CONSTRAINT ck_kit_component_not_self CHECK (kit_sku <> component_sku)
);
CREATE INDEX idx_kit_component_kit_sku ON product_kit_component (kit_sku);
```
Sem FK para `product(sku)` porque a coluna não é única no espaço de nomes compartilhado
pai/variação — a validação (componente existe **e** é `SIMPLES`) fica no service, como já ocorre em
`requireKnownSku` (`EstoqueService.java:321-325`).
Permissão: `ESTOQUE_KIT_MANAGE`.

### V71 — cliente de marketplace

```sql
ALTER TABLE users ADD COLUMN customer_id BIGINT REFERENCES customer(id);
ALTER TABLE users ADD COLUMN user_type   VARCHAR(20) NOT NULL DEFAULT 'OPERATOR';
ALTER TABLE users ADD CONSTRAINT ck_users_type CHECK (user_type IN ('OPERATOR','CUSTOMER'));
ALTER TABLE users ADD CONSTRAINT ck_users_customer_link
    CHECK ((user_type = 'CUSTOMER') = (customer_id IS NOT NULL));
CREATE UNIQUE INDEX uk_users_customer_id ON users (customer_id) WHERE customer_id IS NOT NULL;

INSERT INTO roles (name) VALUES ('ROLE_CUSTOMER') ON CONFLICT (name) DO NOTHING;
```
Permissões (só para `ROLE_CUSTOMER`): `SHOP_CART_OWN`, `SHOP_ORDER_OWN`, `SHOP_CASHBACK_OWN`.

> A unicidade de `username` precisa passar a ser por `(user_type, username)`. Se hoje houver índice
> único simples em `username`, a substituição entra aqui e é ponto de atenção da migração.

### V72 — carrinho

```sql
CREATE TABLE cart (
    id          BIGSERIAL   PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES customer(id),
    status      VARCHAR(20) NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uk_cart_open_customer ON cart (customer_id) WHERE status = 'OPEN';

CREATE TABLE cart_item (
    id       BIGSERIAL     PRIMARY KEY,
    cart_id  BIGINT        NOT NULL REFERENCES cart(id) ON DELETE CASCADE,
    sku      VARCHAR(50)   NOT NULL,
    quantity NUMERIC(14,3) NOT NULL CHECK (quantity > 0),
    CONSTRAINT uk_cart_item UNIQUE (cart_id, sku)
);
```
O carrinho **não guarda preço** — preço é resolvido do catálogo na exibição e congelado só no
checkout. Guardar preço no carrinho cria a promessa de um preço que o sistema não se comprometeu a
honrar.

---

## 5. API

Padrão de `docs/dominios/estoque/README.md`. Todos exigem `bearerAuth`, exceto onde marcado
**público**.

### 5.1 PDV — `/pdv`

| Método | Rota | Permissão | Sucesso | Erros |
|---|---|---|---|---|
| `POST` | `/pdv/sessions` | `PDV_SESSION_MANAGE` | `201` + `Location` | `409 SESSION_ALREADY_OPEN`, `404 WAREHOUSE_NOT_FOUND`, `400 VALIDATION_ERROR` |
| `GET` | `/pdv/sessions` | `PDV_READ` | `200` `PageResult<CashRegisterSessionResponseDTO>` | `400 VALIDATION_ERROR` |
| `GET` | `/pdv/sessions/{id}` | `PDV_READ` | `200` | `404 SESSION_NOT_FOUND` |
| `GET` | `/pdv/sessions/current` | `PDV_READ` | `200` sessão aberta do operador | `404 NO_OPEN_SESSION` |
| `POST` | `/pdv/sessions/{id}/movements` | `PDV_SESSION_MANAGE` | `201` sangria/suprimento | `409 SESSION_CLOSED`, `403 SESSION_NOT_OWNED`, `400 VALIDATION_ERROR` |
| `POST` | `/pdv/sessions/{id}/close` | `PDV_SESSION_CLOSE` | `200` com esperado × contado × diferença | `409 SESSION_CLOSED`, `404 SESSION_NOT_FOUND` |
| `POST` | `/pdv/sessions/{id}/sales` | `PDV_SALE_MANAGE` (+ `PDV_SALE_DISCOUNT` se houver desconto) | `201` `OrderResponseDTO` | `400 INSUFFICIENT_STOCK`, `400 RESERVED_STOCK`, `409 PRODUCT_NOT_PRICED`, `409 SESSION_CLOSED`, `409 DISCOUNT_LIMIT_EXCEEDED`, `409 STOCK_UPDATE_CONFLICT`, `403 SESSION_NOT_OWNED`, `404 CUSTOMER_NOT_FOUND` |
| `GET` | `/pdv/sales/{id}` | `PDV_READ` | `200` | `404 ORDER_NOT_FOUND` |
| `GET` | `/pdv/sessions/{id}/sales` | `PDV_READ` | `200` paginado | `404 SESSION_NOT_FOUND` |
| `POST` | `/pdv/sales/{id}/cancel` | `PDV_SALE_CANCEL` | `200` — estorna estoque e cashback | `409 ORDER_ALREADY_CANCELLED`, `409 SESSION_CLOSED`, `404 ORDER_NOT_FOUND` |

`warehouseCode` **sai do corpo** de `POST .../sales`: passa a vir da sessão.
`unitPrice` **sai** de `SaleItemRequest`; entra `discountAmount` opcional.

### 5.2 Cashback — `/cashback`

| Método | Rota | Permissão | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/cashback/rates` | `CASHBACK_READ` | `200` paginado | — |
| `POST` | `/cashback/rates` | `CASHBACK_RATE_MANAGE` | `201` | `409 CASHBACK_RATE_ALREADY_EXISTS`, `400 VALIDATION_ERROR` |
| `PATCH` | `/cashback/rates/{id}` | `CASHBACK_RATE_MANAGE` | `200` — nulo mantém | `404 CASHBACK_RATE_NOT_FOUND` |
| `GET` | `/cashback/rates/resolve?sku=` | `CASHBACK_READ` | `200` taxa vigente + escopo que venceu | `404 PRODUCT_NOT_FOUND` |
| `GET` | `/cashback/margin-impact?maxShare=` | `CASHBACK_READ` | `200` produtos cuja taxa consome mais que `maxShare` da margem | `400 VALIDATION_ERROR` |
| `GET` | `/cashback/customers/{id}` | `CASHBACK_READ` | `200` saldo disponível, pendente e a expirar | `404 CUSTOMER_NOT_FOUND` |
| `GET` | `/cashback/customers/{id}/entries` | `CASHBACK_READ` | `200` extrato paginado | `404 CUSTOMER_NOT_FOUND` |
| `POST` | `/cashback/customers/{id}/adjust` | `CASHBACK_ADJUST` | `201` ajuste manual auditado | `400 VALIDATION_ERROR` |

`GET /crm/customers/{id}/cashback` (`CrmController.java:226`) deixa de ser placeholder e passa a
delegar ao mesmo caso de uso. `GET /crm/customers/{id}/orders` (`:213`) idem, contra `sales_order`.

### 5.3 Estoque — acréscimos

| Método | Rota | Permissão | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/estoque/stock-balance` | `ESTOQUE_WAREHOUSE_READ` | `200` — passa a devolver `quantity`, `reservedQuantity`, `availableQuantity`; para SKU `KIT`, saldo derivado | `404 WAREHOUSE_NOT_FOUND` |
| `GET` | `/estoque/reservations` | `ESTOQUE_RESERVATION_READ` | `200` paginado por SKU/depósito/pedido | `400 VALIDATION_ERROR` |
| `PUT` | `/estoque/products/{sku}/kit` | `ESTOQUE_KIT_MANAGE` | `200` — define a receita (upsert) | `404 PRODUCT_NOT_FOUND`, `409 KIT_COMPONENT_NOT_SIMPLE`, `409 KIT_SELF_REFERENCE` |
| `GET` | `/estoque/products/{sku}/kit` | `ESTOQUE_PRODUCT_READ` | `200` receita + saldo derivado | `404 PRODUCT_NOT_FOUND` |

### 5.4 Marketplace — `/shop`

| Método | Rota | Permissão | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/shop/catalog` | **público** | `200` — só produtos ativos e precificados, com preço e disponível | `400 VALIDATION_ERROR` |
| `GET` | `/shop/catalog/{sku}` | **público** | `200` | `404 PRODUCT_NOT_FOUND` |
| `POST` | `/shop/register` | **público** (rate-limited) | `201` — cria `Customer` + `User` `ROLE_CUSTOMER` | `409 CUSTOMER_EMAIL_ALREADY_EXISTS`, `429 RATE_LIMIT_EXCEEDED` |
| `GET` | `/shop/cart` | `SHOP_CART_OWN` | `200` carrinho do autenticado | — |
| `PUT` | `/shop/cart/items/{sku}` | `SHOP_CART_OWN` | `200` upsert de quantidade | `404 PRODUCT_NOT_FOUND`, `409 PRODUCT_NOT_PRICED` |
| `DELETE` | `/shop/cart/items/{sku}` | `SHOP_CART_OWN` | `204` | `404 CART_ITEM_NOT_FOUND` |
| `POST` | `/shop/checkout` | `SHOP_ORDER_OWN` | `201` — cria pedido, **reserva** estoque, devolve dados de pagamento | `400 INSUFFICIENT_STOCK`, `409 CART_EMPTY`, `409 PRODUCT_NOT_PRICED`, `409 CASHBACK_LIMIT_EXCEEDED`, `409 STOCK_UPDATE_CONFLICT` |
| `GET` | `/shop/orders` | `SHOP_ORDER_OWN` | `200` — **sempre** filtrado pelo principal | — |
| `GET` | `/shop/orders/{id}` | `SHOP_ORDER_OWN` | `200` | `404 ORDER_NOT_FOUND` — inclusive para pedido de outro cliente |
| `POST` | `/shop/orders/{id}/cancel` | `SHOP_ORDER_OWN` | `200` — só em `AGUARDANDO_PAGAMENTO` | `409 ORDER_NOT_CANCELLABLE`, `404 ORDER_NOT_FOUND` |
| `GET` | `/shop/cashback` | `SHOP_CASHBACK_OWN` | `200` saldo e extrato próprios | — |
| `POST` | `/webhooks/payments/{provider}` | **público**, assinatura HMAC | `200` sempre (idempotente) | `401` assinatura inválida |

> Pedido de outro cliente responde **404, não 403**: 403 confirma que o recurso existe e transforma
> a rota num oráculo de enumeração.

### 5.5 Pedidos (visão operador) — `/orders`

| Método | Rota | Permissão | Sucesso | Erros |
|---|---|---|---|---|
| `GET` | `/orders` | `ORDER_READ` | `200` — filtros por canal, status, período, cliente | `400 VALIDATION_ERROR` |
| `GET` | `/orders/{id}` | `ORDER_READ` | `200` | `404 ORDER_NOT_FOUND` |
| `POST` | `/orders/{id}/status` | `ORDER_FULFILL` | `200` `SEPARADO`/`ENVIADO`/`ENTREGUE` | `409 INVALID_STATUS_TRANSITION` |
| `POST` | `/orders/{id}/cancel` | `ORDER_CANCEL` | `200` — estorna estoque, cashback e pagamento | `409 ORDER_ALREADY_CANCELLED` |

### 5.6 Eventos de auditoria novos (`AuditEvent.EventType`)

`ORDER_CREATED`, `ORDER_PAID`, `ORDER_CANCELLED`, `ORDER_STATUS_CHANGED`,
`CASH_SESSION_OPENED`, `CASH_SESSION_CLOSED`, `CASH_MOVEMENT_REGISTERED`,
`PAYMENT_CAPTURED`, `PAYMENT_REFUNDED`,
`CASHBACK_EARNED`, `CASHBACK_REDEEMED`, `CASHBACK_REVERSED`, `CASHBACK_RATE_CHANGED`,
`STOCK_RESERVED`, `STOCK_RESERVATION_RELEASED`, `KIT_RECIPE_CHANGED`.

Publicados **nos controllers**, como manda a restrição arquitetural (`PdvController.java:90-96` é o
molde). Um evento por operação, não por item — a convenção já estabelecida em EST-C004.

---

## 6. Plano de execução

**Premissa das estimativas:** um desenvolvedor, ~6 horas produtivas por dia, mantendo a disciplina
do repositório — teste unitário de domínio, teste de service com Mockito, teste de controller com
MockMvc standalone, `*SecurityTest` cobrindo 401/403/sucesso por endpoint, IT com `@SpringBootTest`
+ `@Transactional`, README do domínio, collection Postman e `feature-registry.md`. Observando as
entregas anteriores, **teste e documentação são 40–50% do esforço de cada fatia** e estão dentro
dos números abaixo, não fora. Sem JDK no ambiente de análise, as estimativas são de julgamento, não
de medição.

Cada fatia deixa o sistema funcionando e testável. Nenhuma deixa o sistema meio migrado.

| # | Fatia | O que entra | Desbloqueia | Depende de | Esforço |
|---|---|---|---|---|---|
| **0** | **Fundação do pedido** | V65; `Order`/`OrderItem`; preço e custo resolvidos do catálogo e congelados; `discountAmount`; `unitPrice` fora do request; `SaleRepository` ganha leitura; `GET /pdv/sales/{id}` | **Tudo** | — | **5–7 d** |
| **1** | **Ciclo de caixa** | V66; `open`/sangria/suprimento/`close`; sessão amarrada ao operador; depósito vindo da sessão; DTO em `GET /pdv/sessions` (PDV-C002) | Operação real da loja | 0 | **4–5 d** |
| **2** | **Cliente no balcão** | V67; `email` opcional no `Customer`; lookup por CPF/contato; cadastro rápido; `customerId` no pedido; `GET /crm/customers/{id}/orders` real | Cashback | 0 | **3–4 d** |
| **3** | **Pagamento de balcão** | V68; `order_payment`; múltiplas formas; troco; fechamento por forma de pagamento | Conferência de caixa confiável, base do DRE | 1 | **4–5 d** |
| **4** | **Cashback** | V69; taxas por escopo; ledger; ganho na conclusão; resgate no balcão; `/cashback/margin-impact`; `GET /crm/customers/{id}/cashback` real | Programa de fidelidade | 2, 3 | **6–8 d** |
| **5** | **Cancelamento e devolução** | Transições de estado; estorno de estoque (EST-F014); `REVERSED` no cashback; estorno de pagamento | Correção de erro de operação; pré-requisito da reserva | 3, 4 | **4–5 d** |
| — | *Marco: PDV operável de ponta a ponta* | | | | **~26–34 d** |
| **6** | **Kits** | V70; `ProductType`; receita; saldo derivado; custo derivado no `Pricing`; explosão na venda | Venda de combo no balcão e no site | 0 | **4–5 d** |
| **7** | **Reserva de estoque** | V69; `reservedQuantity`; `stock_reservation`; `SAIDA` contra disponível; scheduler de expiração | Marketplace sem overselling | 5 | **5–6 d** |
| **8** | **Autenticação de cliente + catálogo público** | V71; `ROLE_CUSTOMER`; `user_type`; `/shop/catalog`; `/shop/register`; rate limit; ArchUnit exigindo `@PreAuthorize` | Superfície pública | 0 | **6–8 d** |
| **9** | **Carrinho e checkout** | V72; carrinho; checkout criando pedido + reserva; resgate de cashback online; `/shop/orders` com propriedade validada no service | Pedido online completo (pagamento manual) | 7, 8 | **8–10 d** |
| **10** | **Gateway e webhook** | `PaymentGatewayPort` + adapter; PIX; webhook idempotente com HMAC; estorno | Marketplace transacionando de verdade | 9 | **6–8 d** + integração externa |
| — | *Marco: marketplace no ar* | | | | **~29–37 d** |
| **11** | **NFC-e via emissor** | Port fiscal + adapter; campos fiscais no produto; emissão na conclusão; cancelamento na janela legal | Aposentar o processo fiscal atual | 3 | **5–8 d** + certificado, cadastro fiscal e homologação (calendário maior que o esforço) |

**Total: 60–79 dias úteis de desenvolvimento**, fora a homologação fiscal e a contratação do
gateway, que são calendário e não esforço.

### Por que esta ordem

A Fatia 0 vem antes de tudo porque é a **única** que fica mais cara a cada dia de dado real
acumulado. As fatias 1–3 são o que faz a loja parar de abrir caixa por `INSERT` manual — é o que o
dono sente na primeira semana. A 4 é o pedido explícito dele. A 5 existe antes do marketplace
porque reserva sem cancelamento é uma armadilha: estoque travado sem como destravar.

Kits (6) aparece cedo apesar da prioridade baixa no backlog (EST-F015) porque não depende de nada
além da Fatia 0 e é uma das duas coisas que o dono pediu nominalmente — vale entregar enquanto o
marketplace ainda está longe.

---

## 7. Riscos e pontos de não-retorno

Ordenados por custo de reverter depois de haver dado real.

| Risco | Por que é de não-retorno | Mitigação |
|---|---|---|
| **1. Modelo de pedido partido em dois** | Cada relatório, o ledger de cashback e o extrato do cliente nascem com `UNION`. Unificar depois exige migrar dois espaços de id e reescrever todos os consumidores | Fatia 0, **antes** de qualquer volume |
| **2. Vazamento de pedido entre clientes** | Dado de cliente exposto não volta. É o único item deste documento cujo custo não é dinheiro nem tempo | `customerId` sempre do principal, nunca do request; 404 em vez de 403; teste de propriedade obrigatório por endpoint `/shop` |
| **3. Preço e custo não congelados no item** | Toda margem histórica passa a ser recalculada com o custo de hoje. Não há como reconstruir — o custo antigo não está em lugar nenhum | `unit_price`, `cost_price` e `cashback_percent` no `order_item` desde a Fatia 0 |
| **4. Cashback como saldo mutável** | O extrato do cliente deixa de existir. Quando ele reclamar, não há resposta | Ledger append-only desde a Fatia 4; nenhum `UPDATE` em `cashback_entry` |
| **5. Numeração de pedido derivada do `id` serial** | `BIGSERIAL` deixa buracos em rollback de transação. Buraco em numeração de documento fiscal é problema com o fisco | `order_number` de sequência própria, emitido na **conclusão**, não na criação |
| **6. Emissão fiscal construída em casa** | Consome o time por meses e o resultado é pior que o de prateleira; e desfazer significa reintegrar tudo | Emissor terceiro, decidido antes de escrever a primeira linha de fiscal |
| **7. `reserved_quantity` divergindo do ledger de reserva** | Estoque travado invisível: venda não acontece e ninguém sabe por quê | Mesma transação, mesmo `@Version`, `CHECK` no schema, e um endpoint de integridade no molde de `GET /estoque/integrity/orphan-skus` |
| **8. `warehouse_code` como `VARCHAR` sem FK em `sales_order`** | Passivo herdado da V57, mesmo problema de EST-C011: pedido apontando para depósito inexistente | Não corrigir agora (renomear/FK em tabela de pedido é caro); registrar como item de backlog e validar na escrita |
| **9. Cliente e operador no mesmo `users` sem discriminador** | Colisão de `username` entre um cliente que se auto-cadastra e um operador. Resolver depois significa renomear contas de gente real | `user_type` + unicidade composta na Fatia 8, antes do primeiro cadastro público |
| **10. Webhook de pagamento não idempotente** | Gateways reenviam. Reenvio virando segundo pagamento gera pedido pago duas vezes e cashback dobrado | Índice único em `gateway_ref` desde a Fatia 3 (V68), muito antes do gateway existir |
| **11. Alterar `StockBalance.apply` para validar disponível** | Método maduro e no caminho crítico de PDV, compras e balanço de inventário | Fatia 7; suíte de estoque inteira revalidada; `reserved = 0` preserva o comportamento atual por construção |
| **12. Carência de cashback menor que a janela de devolução** | Cliente resgata, devolve, e o saldo fica negativo — ou pior, a devolução é bloqueada | Regra de configuração: carência ≥ janela de devolução, validada no startup |

---

## 8. O que **não** fazer agora

Escopo cortado é decisão de arquitetura, e cada item abaixo é uma decisão, não um esquecimento.

1. **Não construir emissão fiscal própria.** Nunca, em nenhum horizonte deste projeto. Focus NFe ou
   PlugNotas. O diferencial da tabacaria não é saber assinar XML.

2. **Não começar pelo marketplace.** O balcão fatura hoje e o marketplace não existe. Cada semana
   gasta no site é uma semana em que o caixa continua sendo aberto por `INSERT` manual. A Fatia 0
   já protege o futuro do marketplace sem construí-lo.

3. **Não construir motor de cupons e promoções.** O `package-info` do ecommerce prevê `Coupon` e
   `Promotion` ("Leve 3 Pague 2"). Cashback **já é** o mecanismo de fidelidade escolhido pelo dono.
   Dois motores de desconto compondo entre si é exatamente onde nasce o pedido que sai de graça, e
   testar a interação dos dois custa mais que construir cada um.

4. **Não fazer frete e integração logística.** Tabacaria vende na região. Retirada na loja +
   entrega própria com taxa fixa por bairro cobre quase tudo. Integração com Correios ou
   transportadora é um projeto de semanas para um problema que o negócio ainda não tem.

5. **Não fazer preço por variação (EST-F020).** O `Pricing` no SKU pai resolve o caso da tabacaria
   (sabores da mesma essência custam o mesmo). Grade com preços distintos se modela como produtos
   separados até doer de verdade.

6. **Não fazer kit físico nem kit aninhado.** Um nível, virtual. As duas restrições eliminam classes
   inteiras de bug e nenhuma delas impede expandir depois sem migrar dado.

7. **Não fazer PDV offline / PWA.** Balcão offline significa sincronizar saldo de estoque **e**
   numeração fiscal entre instâncias. É um projeto inteiro, com os piores bugs possíveis, para
   resolver uma queda de internet que se resolve com um 4G de backup.

8. **Não fazer multi-loja ou multi-tenant.** O código inteiro assume single-tenant e a
   documentação declara isso. Introduzir tenant agora contamina toda consulta em troca de zero
   valor imediato.

9. **Não fazer custo médio ponderado (EST-F007) antes da Fatia 4.** É desejável para calibrar o
   cashback com precisão, mas o `costPrice` manual da V63 já entrega a ordem de grandeza — e a
   ordem de grandeza é o que decide se a taxa do carvão é 2% ou 8%. Depois do cashback rodando, o
   custo médio refina; antes, ele atrasa.

10. **Não fazer painel administrativo agora.** O dono pediu um, e ele é necessário — mas é frontend
    sobre uma API que ainda vai mudar de forma na Fatia 0. Construir tela sobre `SaleItem.unitPrice`
    é construir para jogar fora. O Swagger e a collection Postman sustentam a operação interna até
    a Fatia 5.

---

## 9. Itens de backlog decorrentes

**Registrados em 2026-07-28** nos READMEs dos módulos, nas séries de ID que cada um já usa. Três
dos IDs sugeridos na primeira versão desta seção colidiam com IDs em uso (`PDV-C003` foi consumido
por EST-C004, `ECM-C001` é a auditoria do módulo, e a série `CRM-C0xx` já ia até `C004`); a coluna
abaixo traz o ID **efetivamente atribuído**.

| ID | Módulo | Item | Fatia |
|---|---|---|---|
| `PDV-F003` | vendas-balcao | Unificar `Sale` em `Order` com discriminador de canal | 0 |
| `PDV-F004` | vendas-balcao | Preço e custo resolvidos do catálogo e congelados; `discountAmount` sob `PDV_SALE_DISCOUNT` | 0 |
| `PDV-F005` | vendas-balcao | Leitura de venda — `SaleRepository` é write-only hoje | 0 |
| `PDV-F006` | vendas-balcao | Pagamento com múltiplas formas e troco | 3 |
| `PDV-C004` | vendas-balcao | Amarrar sessão ao operador em `registerSale` (isolamento documentado no README:72-75) | 1 |
| `PDV-F001`/`F002` | vendas-balcao | *(já existem)* Ciclo de caixa e modelos de movimento — modelo detalhado em §2.7 | 1 |
| `CRM-C005` | crm | `Customer.email` opcional com unicidade por CPF — bloqueia cadastro rápido no balcão | 2 |
| `CRM-F001` | crm | `GET /crm/customers/{id}/orders` e `/cashback` deixam de ser placeholder | 2 |
| `CRM-F002` | crm | Lookup por CPF/contato + cadastro rápido no balcão, sob `CRM_CUSTOMER_LOOKUP` | 2 |
| `CRM-F003` | crm | Programa de cashback: taxas por escopo, ledger append-only e `/cashback/margin-impact` | 4 |
| `EST-F013` | estoque | *(já existe)* `StockReservation` — modelo detalhado em §2.2; migration V64 **já aplicada**; reserva no **checkout**, não no carrinho | 7 |
| `EST-F014` | estoque | *(já existe)* Estorno/devolução — sobe de prioridade, precede a reserva | 5 |
| `EST-F015` | estoque | *(já existe)* Kit — decidido virtual, um nível (§2.10) | 6 |
| `EST-F021` | estoque | `reservedQuantity` em `StockBalance` e `SAIDA` contra disponível | 7 |
| `EST-F022` | estoque | Custo derivado de kit em `findPricingBySku` | 6 |
| `EST-C013` | estoque | Endpoint de integridade para divergência entre `reserved_quantity` e o ledger de reserva | 7 |
| `ECM-F001` | ecommerce | Autenticação de cliente com `ROLE_CUSTOMER` e `user_type` | 8 |
| `ECM-F002` | ecommerce | Catálogo público com rate limit | 8 |
| `ECM-F003` | ecommerce | Carrinho e checkout com reserva | 9 |
| `ECM-F004` | ecommerce | `PaymentGatewayPort` + webhook idempotente | 10 |
| `ECM-C002` | ecommerce | Descartar o record `Cart` atual — não há nada aproveitável | 9 |
| `FIN-F001` | financeiro | Cashback como provisão de passivo no DRE, reconhecida no ganho e não no resgate | 4 |
| `FIN-F002` | financeiro | NFC-e via emissor terceiro — port fiscal + adapter, campos fiscais no produto | 11 |
| `PLAT-C034` | plataforma | Teste ArchUnit exigindo `@PreAuthorize` em todo método de controller fora de `/auth` e `/shop` | 8 |
| `PLAT-C030` | plataforma | *(já existe)* Rate limit — vira bloqueante na Fatia 8 | 8 |

Três itens já existentes receberam **nota de decisão** no README de estoque em vez de item novo,
porque o plano mudou o julgamento sobre eles e não o escopo: `EST-F007` (custo médio — não antes da
Fatia 4, §8.9), `EST-F012` (transferência entre depósitos — só com um segundo local físico real,
§2.2) e `EST-F020` (preço por variação — desaconselhado, §8.5).
