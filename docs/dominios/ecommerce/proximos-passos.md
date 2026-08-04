# E-commerce / marketplace — roteiro para sair do esqueleto

**Criado em:** 2026-07-28, a partir do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Status em 2026-08-03: MARKETPLACE COMPLETO.** Fatias 8, 9 e 10 (ECM-F001–F004) todas entregues —
cliente se cadastra, navega o catálogo, monta carrinho, faz checkout, paga via InfinitePay
(webhook confirma automaticamente) e acompanha o pedido. Não há mais fatia numerada pendente
neste módulo. O prompt abaixo fica como **histórico** de como o módulo foi construído — para
trabalho novo aqui, veja "O que falta agora" no fim deste arquivo, não o prompt.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista.

---

## Prompt original (histórico — módulo já concluído, não recomece do zero)

```
Continue o desenvolvimento do módulo ECOMMERCE (marketplace) do Mahal backend.

ANTES DE COMEÇAR — confirme comigo que as Fatias 0 a 5 do plano já foram entregues (PDV
operável de ponta a ponta: pedido unificado, ciclo de caixa, cliente no balcão, pagamento,
cancelamento). Se não foram, o certo é NÃO trabalhar neste módulo ainda, e me dizer isso.

FONTE DA VERDADE
Backlog: docs/dominios/ecommerce/README.md, seção "## Backlog do Módulo".
Desenho e justificativa: docs/plano-pdv-marketplace.md — leia §2.2, §2.6, §2.9 e §5.4
antes de escrever qualquer linha. ATENÇÃO: §2.6 supõe Mercado Pago; o gateway real escolhido
foi InfinitePay — a API de verdade diverge do desenho do plano em vários pontos (ver seção
"Gateway de pagamento InfinitePay + webhook" no README).

ESTADO ATUAL (revisado 2026-08-03) — TODAS AS FATIAS CONCLUÍDAS
ECM-F001 (autenticação), ECM-F002 (catálogo), ECM-F003+ECM-C002 (carrinho/checkout) e ECM-F004
(gateway InfinitePay + webhook) JÁ FORAM ENTREGUES — não recomece do zero. Existe: UserType
{OPERATOR, CUSTOMER}, users.user_type/customer_id (V76), ROLE_CUSTOMER + SHOP_CART_OWN/
SHOP_ORDER_OWN semeados (SHOP_CASHBACK_OWN semeada mas SEM endpoint ainda, ver nota abaixo),
ShopController (público: POST /shop/register, GET /shop/catalog, GET /shop/catalog/{sku}),
ShopAccountController (autenticado: GET/PUT/DELETE /shop/cart[/items/{sku}], POST /shop/checkout,
GET /shop/orders, GET /shop/orders/{id}, POST /shop/orders/{id}/cancel) e PaymentWebhookController
(público: POST /webhooks/payments/{provider}). ShopUseCase/ShopService cobre
registerCustomer/listCatalog/getCatalogItem/getCart/upsertCartItem/removeCartItem/checkout/
listMyOrders/getMyOrder/cancelMyOrder. Login do cliente é o MESMO POST /auth/login — não crie
/shop/login.
cart/cart_item (V78) — o carrinho NÃO guarda preço, só sku+quantidade; Cart.java/CartItem.java
em core/domain/model/ecommerce. Checkout resolve preço/custo/taxa de cashback do catálogo NA
HORA (nunca do que estava no carrinho), cria o pedido via Order.openMarketplace (já existia no
domínio, ganhou o primeiro caller), reserva estoque via EstoqueUseCase.reserveStock com owner
reference Order.reservationOwnerReference(orderId), cria a cobrança no InfinitePay
(PaymentGatewayPort.createCheckoutLink, orderNsu = Order.id().toString()) e só esvazia o
carrinho se tudo suceder na mesma transação.
NOVO CONCEITO transversal introduzido pelo catálogo (ECM-F002) e REAPROVEITADO pelo checkout
sem inventar nada: EstoqueUseCase.getDefaultWarehouse() resolve o depósito único da loja a
partir de system_config.estoque.warehouse.default-code (chave semeada em branco pela V77 —
nenhuma migration inventa um código real, o operador configura via PUT /system/config/{key}
depois de criar o depósito em POST /estoque/warehouses). Chave ausente/em branco lança
DefaultWarehouseNotConfiguredException (503 DEFAULT_WAREHOUSE_NOT_CONFIGURED).
PaymentWebhookService (core/service) confirma o pagamento: reconsulta o InfinitePay via
PaymentGatewayPort.checkPayment (nunca confia no corpo da notificação — o InfinitePay não assina
o webhook), marca OrderPayment CAPTURED (OrderPayment.confirmCaptured, única exceção ao ledger
append-only), chama Order.paid() e cashbackUseCase.recordEarnedForOrder. GET /shop/cashback
(saldo/extrato do cliente) segue sem endpoint — não é mais "sequenciamento", é só falta de
pedido; o cashback já é gravado de verdade desde o webhook.
EcommerceService.listCarts devolve página vazia com um // TODO — continua stub de propósito, é
a visão do ADMIN (/ecommerce/carts), nunca fez parte do pedido do cliente final (/shop/**).

O QUE JÁ EXISTE E NÃO DEVE SER RECRIADO
- A reserva de estoque (EST-F013/EST-F021) e o checkout que a consome (ECM-F003) JÁ ESTÃO
  IMPLEMENTADOS: EstoqueUseCase.reserveStock/consumeReservation/releaseReservation/
  releaseReservationsByOwner/expireReservations + ShopService.checkout chamando reserveStock
  por item. stock_reservation.owner_reference usa o formato "ORDER:{id}" via
  Order.reservationOwnerReference — já em uso, não reinvente.
- Toda a stack de autenticação (JWT, refresh rotation, TOTP, verificação de e-mail, reset
  de senha, OAuth Google, rate limit de login). Reconstruir isso para o cliente final seria
  o erro mais caro disponível neste projeto.
- adjustStock como única porta de escrita de saldo, com validação de SKU, depósito ativo,
  @Version e alerta de reposição.
- Cancelamento de pedido do cliente (POST /shop/orders/{id}/cancel) REAPROVEITA
  OrderUseCase.cancelOrder (Fatia 5) por inteiro — ShopService só resolve customerId e checa
  propriedade antes de delegar. Não duplique a lógica de liberar reserva.
- PaymentGatewayPort/InfinitePayAdapter/StubPaymentGatewayAdapter (ECM-F004) JÁ EXISTEM. Não
  crie um segundo mecanismo de gateway nem reintroduza Mercado Pago sem decisão explícita do
  dono do produto — a troca de gateway já foi feita uma vez, custou retrabalho de pesquisa.

ORDEM — TODAS CONCLUÍDAS
 1. ECM-F001 — CONCLUÍDO (2026-08-03). Autenticação de cliente: UserEntity com ROLE_CUSTOMER e
    discriminador user_type, ligado 1:1 ao Customer do CRM por users.customer_id.
 2. ECM-F002 — CONCLUÍDO (2026-08-03). Catálogo público. Fatia 8 fechada.
 3. ECM-F003 + ECM-C002 — CONCLUÍDO (2026-08-03). Carrinho e checkout com reserva. Fatia 9 fechada.
 4. ECM-F004 — CONCLUÍDO (2026-08-03). Gateway InfinitePay + webhook. Fatia 10 fechada.

DECISÕES JÁ TOMADAS — não reabrir
- Cliente do marketplace usa o MESMO UserEntity, com ROLE_CUSTOMER e user_type ∈
  {OPERATOR, CUSTOMER}, unicidade por (user_type, username). Não crie CustomerAccount. §2.9.
- REGRA DURA DE SEGURANÇA: o marketplace NUNCA aceita customerId vindo do request. É sempre
  resolvido a partir do principal autenticado, DENTRO do service. @PreAuthorize não sabe
  dizer "só os seus pedidos".
- Pedido de outro cliente responde 404, NÃO 403 — 403 confirma que o recurso existe e
  transforma a rota num oráculo de enumeração. É o único item do plano cujo custo de errar
  é irreversível: dado de cliente exposto não volta.
- Reserva acontece no CHECKOUT, não na entrada no carrinho. Carrinho abandonado é a regra,
  não a exceção, e reservar ali seguraria estoque por horas. TTL de 30 min (configurável em
  estoque.reservation.default-ttl, hoje PT30M).
- UM depósito só. NÃO use WarehouseType.ECOMMERCE para separar canal — numa loja só, a
  prateleira é uma só, e partir o pool geraria rebalanceamento manual permanente. §2.2.
- O carrinho NÃO guarda preço. Preço é resolvido do catálogo na exibição e congelado só no
  checkout. Guardá-lo cria a promessa de um preço que o sistema não se comprometeu a honrar.
- O webhook é a superfície mais perigosa do projeto: público, assinatura HMAC validada,
  IDEMPOTENTE por gateway_ref (índice único), e o valor pago se confirma CONSULTANDO o
  gateway — nunca lendo o payload. Gateways reenviam; reenvio virando segundo pagamento
  gera pedido pago duas vezes e cashback dobrado.
- FORA DE ESCOPO por decisão, não por esquecimento: cupom e promoção (o cashback já é o
  mecanismo de fidelidade; dois motores de desconto compondo é onde nasce o pedido que sai
  de graça), frete e integração logística (retirada + entrega própria por bairro cobre a
  operação regional), PDV offline/PWA, multi-loja/multi-tenant. §8.

PRÉ-REQUISITO TRANSVERSAL — RESOLVIDO em 2026-08-03
PLAT-C034 (ArchUnit exigindo @PreAuthorize em todo controller fora de /auth e /shop): feito,
arch/SecurityArchitectureTest.java. PLAT-C030 (rate limit) na parte que bloqueava a Fatia 8
(/shop/register) também: feito, LoginRateLimitingFilter. O restante de PLAT-C030 (demais
endpoints de negócio, ex. export do CRM) segue aberto e é independente da Fatia 8.

DECISÃO ADICIONAL TOMADA em 2026-08-03 — NÃO REABRIR: colisão de username entre cliente e
operador. O plano original (§2.9) propunha permitir a colisão de verdade via constraint
composta (user_type, username). Investigação mostrou que isso obrigaria rate limit, lockout,
refresh token, TOTP e blocklist — hoje TODOS chaveados só por username puro, em
AuthService/CustomUserDetailsService/JwtAuthenticationFilter — a virarem cientes do tipo, uma
mudança bem maior e mais arriscada em código de segurança estável do que o plano estimava.
Decisão confirmada com o usuário: manter a constraint composta no schema (rede de segurança,
nunca de fato acionada), mas a aplicação (UserService.createCustomerAccount) IMPEDE a colisão
real checando o email contra findByUsername E findByEmail, cruzando os dois tipos, antes de
criar a conta. Nenhuma outra peça da stack de autenticação precisou mudar. Login do cliente é
o MESMO POST /auth/login — não existe /shop/login.

COMO TRABALHAR
- Um item por vez. Ao terminar cada um, PARE e me mostre o resultado.
- TDD, na ordem domínio → service → persistência → controller → migration.
- Todo endpoint de /shop precisa de teste de PROPRIEDADE, não só de permissão: um cliente
  autenticado tentando ler o pedido de outro tem que receber 404.
- NÃO execute ./mvnw (sem JDK no WSL). Me entregue o comando pronto.
- NÃO faça commit.

Comece confirmando o estado das Fatias 0–5 e me apresentando o plano do ECM-F001.
```

---

## Por que esta ordem

| Agrupamento | Razão |
|---|---|
| Autenticação (F001) antes de catálogo (F002) | O catálogo é público, mas o ramo `/shop/**` inteiro precisa das regras de `SecurityConfig` e do discriminador `user_type` para existir sem colidir com o `username` dos operadores. Resolver a colisão depois significa renomear contas de gente real. |
| Catálogo antes de carrinho | O carrinho resolve preço do catálogo. Sem `/shop/catalog` funcionando, o carrinho não tem de onde ler. |
| Carrinho e checkout juntos, com ECM-C002 | O record `Cart` atual é descartado no mesmo movimento — não há por que manter um modelo vazio enquanto o novo é escrito. |
| Gateway (F004) por último | O checkout precisa funcionar com pagamento manual antes de haver gateway. Isso permite testar o pedido online de ponta a ponta sem depender de contratação externa — e a contratação é calendário, não esforço. |

## Riscos a considerar antes de encarar a lista

**Autorização por linha é o risco irreversível do projeto.** `@PreAuthorize` resolve "quem pode
chamar", não "quais linhas pode ver". Todo endpoint `/shop` precisa resolver o `customerId` do
principal e validar propriedade no service. Um vazamento de pedido entre clientes não se conserta
com deploy.

**A colisão de `username` só aparece com cliente real cadastrado.** Um cliente que se auto-cadastra
vai colidir com username de operador em algum momento; resolver depois significa renomear contas de
gente real. Por isso `user_type` entra na Fatia 8, **antes do primeiro cadastro público**, e não
quando o problema aparecer.

**O gateway tem calendário próprio.** Contratação, credenciais de sandbox e homologação não são
esforço de desenvolvimento e não comprimem. Comece a conversa comercial junto com a Fatia 9, não
depois dela.

## O que "módulo entregue" significa aqui

Fechar F001, F002, F003, F004 e C002 — cliente se cadastra, navega o catálogo, monta carrinho, faz
checkout com reserva de estoque, paga por PIX e acompanha o pedido; o operador vê tudo pela visão
`/orders`. O `ECM-C001` (documentar o módulo no padrão de estoque) sai naturalmente do caminho,
porque o README precisa ser escrito de qualquer forma enquanto as features são entregues.
