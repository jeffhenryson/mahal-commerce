# Domínio: ecommerce

**Status:** 🟡 Esqueleto criado — implementação pendente
**Pacote Java:** `com.cernecommerce...ecommerce`
**Rota HTTP base:** `/ecommerce`

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
