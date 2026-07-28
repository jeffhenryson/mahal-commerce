# E-commerce / marketplace — roteiro para sair do esqueleto

**Criado em:** 2026-07-28, a partir do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Para quê:** dar a ordem de execução do marketplace — e, antes disso, deixar registrado **por que
ele não é o primeiro** a ser construído.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista.

> **Não comece por aqui.** O balcão fatura hoje e o marketplace não existe. Cada semana gasta no
> site é uma semana em que o caixa continua sendo aberto por `INSERT` manual. As Fatias 0 a 5 do
> plano (PDV, CRM, estoque) vêm primeiro; a Fatia 0 já protege o futuro do marketplace — o pedido
> nasce unificado — sem construí-lo.

---

## Prompt para colar numa sessão nova

```
Continue o desenvolvimento do módulo ECOMMERCE (marketplace) do Mahal backend.

ANTES DE COMEÇAR — confirme comigo que as Fatias 0 a 5 do plano já foram entregues (PDV
operável de ponta a ponta: pedido unificado, ciclo de caixa, cliente no balcão, pagamento,
cancelamento). Se não foram, o certo é NÃO trabalhar neste módulo ainda, e me dizer isso.

FONTE DA VERDADE
Backlog: docs/dominios/ecommerce/README.md, seção "## Backlog do Módulo".
Desenho e justificativa: docs/plano-pdv-marketplace.md — leia §2.2, §2.6, §2.9 e §5.4
antes de escrever qualquer linha.

ESTADO ATUAL
O módulo é um ESQUELETO. Não existe, em nenhuma forma: Order, checkout, pagamento,
gateway, webhook, frete, catálogo público, autenticação de cliente final, cupom, promoção.
EcommerceService.listCarts (:20-24) devolve página vazia com um // TODO.
core/domain/model/ecommerce/Cart.java:11-18 é um record sem comportamento e SEM ITENS —
não há nada a aproveitar, descarte (ECM-C002) em vez de tentar evoluir.

O QUE JÁ EXISTE E NÃO DEVE SER RECRIADO
- A reserva de estoque (EST-F013/EST-F021) JÁ ESTÁ IMPLEMENTADA em core, ports e
  persistência: EstoqueUseCase.reserveStock / consumeReservation / releaseReservation /
  releaseReservationsByOwner / expireReservations, com stock_balance.reserved_quantity sob
  o @Version existente e o ledger stock_reservation (migration V64). O checkout CONSOME
  essa porta; não escreva um segundo mecanismo de reserva.
  ATENÇÃO: stock_reservation.owner_reference é VARCHAR(80), texto livre, e o COMMENT da
  coluna registra a intenção de virar "ORDER:{id}" quando o pedido existir. Use exatamente
  esse formato no checkout.
- Toda a stack de autenticação (JWT, refresh rotation, TOTP, verificação de e-mail, reset
  de senha, OAuth Google, rate limit de login). Reconstruir isso para o cliente final seria
  o erro mais caro disponível neste projeto.
- adjustStock como única porta de escrita de saldo, com validação de SKU, depósito ativo,
  @Version e alerta de reposição.

ORDEM
 1. ECM-F001 — autenticação de cliente. Reaproveita UserEntity com ROLE_CUSTOMER e
    discriminador user_type, ligado 1:1 ao Customer do CRM por users.customer_id.
 2. ECM-F002 — catálogo público. Junto com a 1 fecha a Fatia 8.
 3. ECM-F003 + ECM-C002 — carrinho e checkout com reserva (Fatia 9).
 4. ECM-F004 — gateway e webhook (Fatia 10).

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

PRÉ-REQUISITO TRANSVERSAL
PLAT-C034 (ArchUnit exigindo @PreAuthorize em todo controller fora de /auth e /shop) e
PLAT-C030 (rate limit) precisam estar prontos ANTES da Fatia 8. Motivo: SecurityConfig
termina em anyRequest().authenticated(), então assim que ROLE_CUSTOMER existir, "autenticado"
passa a incluir cliente final — e qualquer endpoint de operador sem @PreAuthorize vira
exposição. Hoje é inócuo porque todos têm; a garantia é que precisa existir.

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
