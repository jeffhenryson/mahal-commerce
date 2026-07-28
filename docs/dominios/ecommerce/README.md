# Domínio: ecommerce

**Status:** 🟡 Esqueleto criado — implementação pendente
**Pacote Java:** `com.cernecommerce...ecommerce`
**Rota HTTP base:** `/ecommerce`
**Última atualização deste doc:** 2026-07-27 (seção de Segurança e Infraestrutura)

## Objetivo

Loja online: catálogo, carrinho, promoções e pagamentos.

## Escopo planejado

- **Carrinho online:** `Cart` + `CartItem`.
- **Cupons:** `Coupon` (código, validade, regras de desconto).
- **Motor de promoções:** regras configuráveis (ex.: **Leve 3 Pague 2**) via `Promotion`.
- **Integrações de pagamentos:** checkout com gateways (`PaymentGatewayPort`).

## Estrutura hexagonal (criada)

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/ecommerce/Cart` |
| ports/in | `core/ports/in/EcommerceUseCase` |
| ports/out | `core/ports/out/ecommerce/CartRepository` |
| service | `core/service/EcommerceService` (stub, wired em `CoreBeanConfig`) |
| adapter/in | `adapter/in/controller/EcommerceController` → `GET /ecommerce/carts?page&size` (stub, retorna `PageResult<Cart>` vazio) |

## Segurança e Infraestrutura

> Transversal em [`docs/security.md`](../../security.md) e
> [`docs/infrastructure.md`](../../infrastructure.md); modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura).

**O que já existe.** `ECOMMERCE_READ` (criada na V53 com `ON CONFLICT DO NOTHING`, concedida a
`ROLE_ADMIN`; semeada em `dev` por `SeedConfig`/`DevRoleBootstrapConfig`) protege o único
endpoint do módulo, `GET /ecommerce/carts`. O `@PreAuthorize` foi acrescentado em **C004**, que
tirou os controllers stub do fallback genérico `anyRequest().authenticated()`. Não há tabela,
auditoria, rate limit nem infra própria — o service devolve página vazia.

**O que este módulo vai precisar quando sair do esqueleto.** É o único domínio que expõe
superfície a usuário **não autenticado da loja** — hoje toda rota fora de `/auth/**` exige token,
e o modelo de identidade atual só conhece usuários internos:

- [ ] Permissões separando o que é do cliente final e o que é do operador (`ECOMMERCE_MANAGE`,
      checkout, cupons), em vez de um único `*_READ`.
- [ ] Identidade do comprador: carrinho e checkout precisam de dono, e o sistema não tem
      isolamento por usuário fora de auth ([`plataforma`](../plataforma/README.md#isolamento-de-dados)).
- [ ] **Rate limit obrigatório** em checkout, cupom e criação de carrinho — hoje o
      `LoginRateLimitingFilter` só cobre `/auth/**` (PLAT-C030).
- [ ] `AuditEvent` para checkout e aplicação de cupom (valor financeiro).
- [ ] Reserva de estoque para evitar overselling — `EST-F013`, cruzamento com `estoque`.
- [ ] Segredos do gateway de pagamento no fluxo de `.env` + validadores de startup, e webhook de
      pagamento com verificação de assinatura (o backend não tem hoje nenhum endpoint que
      autentique por assinatura HMAC).
- [ ] Dado pessoal do comprador entra em escopo de LGPD — ver
      [`plataforma`](../plataforma/README.md#conformidade-lgpd).

## Testes no Postman

Coleção do módulo: [`ecommerce.postman_collection.json`](ecommerce.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/ecommerce/ecommerce.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

A coleção valida o que já existe (`GET /ecommerce/carts`): o contrato de `PageResult`, a
validação de `page`/`size` e a proteção por `ECOMMERCE_READ` — e serve de esqueleto para
crescer junto com o módulo.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| ECM-F001 | 🟡 Média | Feature | autenticacao-de-cliente-final | Cliente do marketplace reaproveita `UserEntity` com `ROLE_CUSTOMER` e discriminador `user_type ∈ {OPERATOR, CUSTOMER}` (unicidade por `(user_type, username)`), ligado 1:1 ao `Customer` do CRM por `users.customer_id`. Reconstruir hash de senha, verificação de e-mail, reset, TOTP, rotação de refresh e OAuth para o cliente final seria o caminho mais curto para um bug de autenticação em superfície pública. Migration V71. Fatia 8 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.9. | Pendente |
| ECM-F002 | 🟡 Média | Feature | catalogo-publico | `GET /shop/catalog` e `/shop/catalog/{sku}` públicos, expondo só produto ativo e precificado, com preço e disponível. Ramo `/shop/**` com regras próprias em `SecurityConfig`. Exige rate limit em catálogo e sobretudo em `/shop/register`, que cria linhas (PLAT-C030 vira bloqueante aqui). Fatia 8. | Pendente |
| ECM-F003 | 🟡 Média | Feature | carrinho-e-checkout | `cart`/`cart_item` (V72) e checkout criando pedido + reserva de estoque. O carrinho **não guarda preço** — preço é resolvido do catálogo na exibição e congelado só no checkout; guardá-lo cria a promessa de um preço que o sistema não se comprometeu a honrar. Regra dura: `customerId` **nunca** vem do request, sempre do principal autenticado, e pedido de outro cliente responde **404, não 403** — 403 confirma a existência do recurso e transforma a rota num oráculo de enumeração. Fatia 9, §2.9. | Pendente |
| ECM-F004 | 🟡 Média | Feature | gateway-de-pagamento-e-webhook | `PaymentGatewayPort` em `core/ports/out/ecommerce` (o `package-info` do pacote já antecipa) + adapter; PIX via Mercado Pago como escolha pragmática. O webhook é a superfície mais perigosa do projeto: público, assinatura HMAC validada, **idempotente por `gateway_ref`** (gateways reenviam, e reenvio virando segundo pagamento gera pedido pago duas vezes e cashback dobrado), e o valor pago se confirma consultando o gateway — nunca lendo o payload. Fatia 10, §2.6. | Pendente |
| ECM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README ainda no molde de esqueleto: faltam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. Rode `/1-analise ecommerce`. Padrão: [`estoque`](../estoque/README.md). | Pendente |
| ECM-C002 | 🟢 Melhoria | Correção | descartar-o-record-cart-atual | `core/domain/model/ecommerce/Cart.java:11-18` é um record sem comportamento e **sem itens**, e `EcommerceService.listCarts` (`:20-24`) devolve página vazia com um `// TODO`. Não há nada a aproveitar — descartar junto com ECM-F003 em vez de tentar evoluir. | Pendente |

> `EST-F013` (`reserva-estoque-checkout`) é um cruzamento `ecommerce↔estoque` e está rastreado
> em [`estoque`](../estoque/README.md#backlog-do-módulo). A decisão de reservar no **checkout** e
> não no carrinho está em [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.2.

> **Cupom e promoção saíram do escopo** (§8.3): o cashback já é o mecanismo de fidelidade
> escolhido, e dois motores de desconto compondo entre si é exatamente onde nasce o pedido que sai
> de graça. **Frete e integração logística também** (§8.4): retirada na loja + entrega própria com
> taxa fixa por bairro cobre a operação regional de uma tabacaria.

## Próximos passos

Roteiro completo, com prompt pronto para colar numa sessão nova, em
[`proximos-passos.md`](proximos-passos.md).

O marketplace **não** é por onde começar — o balcão fatura hoje e o site não existe. A Fatia 0 do
[`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) protege o futuro do marketplace
(pedido unificado) sem construí-lo. Ordem daqui:

- [ ] **ECM-F001 + ECM-F002** — Fatia 8: autenticação de cliente e catálogo público.
- [ ] **ECM-F003 + ECM-C002** — Fatia 9: carrinho e checkout com reserva; depende de EST-F013.
- [ ] **ECM-F004** — Fatia 10: gateway e webhook.
- [ ] `PaymentGatewayPort` + adapter de gateway; adapter de persistência de carrinho e pedido.
- [ ] Permissões RBAC do domínio (`SHOP_CART_OWN`, `SHOP_ORDER_OWN`, `SHOP_CASHBACK_OWN`) +
      `@PreAuthorize` nos endpoints.
