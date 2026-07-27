# Domínio: logistica

**Status:** 🟡 Esqueleto criado — implementação pendente
**Pacote Java:** `com.cernecommerce...logistica`
**Rota HTTP base:** `/logistica`
**Última atualização deste doc:** 2026-07-27 (seção de Segurança e Infraestrutura)

## Objetivo

Controle de expedição e entregas dos pedidos.

## Escopo planejado

- **Expedição:** status de pedidos (separação → despachado → em rota → entregue).
- **Clique e retire:** retirada na loja (`PickupOrder`).
- **Rotas de motoboy:** roteirização de entregas locais (`DeliveryRoute`).
- **Transportadora:** integração e etiquetas (`CarrierLabel` / `CarrierPort`).

## Estrutura hexagonal (criada)

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/logistica/Shipment` |
| ports/in | `core/ports/in/LogisticaUseCase` |
| ports/out | `core/ports/out/logistica/ShipmentRepository` |
| service | `core/service/LogisticaService` (stub, wired em `CoreBeanConfig`) |
| adapter/in | `adapter/in/controller/LogisticaController` → `GET /logistica/shipments?page&size` (stub, retorna `PageResult<Shipment>` vazio) |

## Segurança e Infraestrutura

> Transversal em [`docs/security.md`](../../security.md) e
> [`docs/infrastructure.md`](../../infrastructure.md); modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura).

**O que já existe.** `LOGISTICA_READ` (criada na V53 com `ON CONFLICT DO NOTHING`, concedida a
`ROLE_ADMIN`; semeada em `dev` por `SeedConfig`/`DevRoleBootstrapConfig`) protege o único
endpoint do módulo, `GET /logistica/shipments`. O `@PreAuthorize` foi acrescentado em **C004**,
que tirou os controllers stub do fallback genérico `anyRequest().authenticated()`. Não há tabela,
auditoria nem infra própria — o service devolve página vazia.

**O que este módulo vai precisar quando sair do esqueleto.** É o domínio que mais vai lidar com
endereço de cliente e com atores externos (motoboy, transportadora):

- [ ] Permissões separando consulta de expedição, atualização de status e roteirização — um
      motoboy não deve ver a base inteira de entregas.
- [ ] Escopo por entregador: hoje não existe isolamento por usuário fora de auth
      ([`plataforma`](../plataforma/README.md#isolamento-de-dados)); qualquer um com a permissão
      vê tudo.
- [ ] Rastreio para o cliente final: se a consulta de status for pública, precisa de token
      opaco por pedido (não id sequencial) e rate limit — hoje nenhuma rota de negócio é
      limitada (PLAT-C030).
- [ ] `AuditEvent` nas mudanças de status (é o registro de quem despachou e quem entregou).
- [ ] Endereço de entrega é dado pessoal: entra no mesmo escopo de LGPD do
      [`crm`](../crm/README.md#isolamento-de-dados-e-dado-pessoal).
- [ ] Credenciais da transportadora (`CarrierPort`) no fluxo de `.env` + validadores de startup
      ([`docs/infrastructure.md`](../../infrastructure.md#variáveis-de-ambiente-e-segredos)).

## Testes no Postman

Coleção do módulo: [`logistica.postman_collection.json`](logistica.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/logistica/logistica.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

A coleção valida o que já existe (`GET /logistica/shipments`): o contrato de `PageResult`, a
validação de `page`/`size` e a proteção por `LOGISTICA_READ` — e serve de esqueleto para
crescer junto com o módulo.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| LOG-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README ainda no molde de esqueleto: faltam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. Rode `/1-analise logistica`. Padrão: [`estoque`](../estoque/README.md). | Pendente |

## Próximos passos

- [ ] Modelos: `PickupOrder`, `DeliveryRoute`, `CarrierLabel`.
- [ ] Casos de uso: `dispatch`, `updateStatus`, `assignMotoboyRoute`, `registerPickup`.
- [ ] Ports out: `CarrierPort` (integração com transportadora).
- [ ] Adapter de persistência: entities JPA + repository impl + migration Flyway.
- [ ] Permissões RBAC do domínio + `@PreAuthorize` nos endpoints.
