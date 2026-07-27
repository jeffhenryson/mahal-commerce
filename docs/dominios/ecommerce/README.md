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
| ECM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README ainda no molde de esqueleto: faltam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. Rode `/1-analise ecommerce`. Padrão: [`estoque`](../estoque/README.md). | Pendente |

> `EST-F013` (`reserva-estoque-checkout`) é um cruzamento `ecommerce↔estoque` e está rastreado
> em [`estoque`](../estoque/README.md#backlog-do-módulo).

## Próximos passos

- [ ] Modelos: `CartItem`, `Coupon`, `Promotion`, `CheckoutOrder`.
- [ ] Casos de uso: `addToCart`, `applyCoupon`, `evaluatePromotions`, `checkout`.
- [ ] Ports out: `CouponRepository`, `PaymentGatewayPort`.
- [ ] Adapter de persistência + adapter de gateway de pagamento.
- [ ] Permissões RBAC do domínio + `@PreAuthorize` nos endpoints.
