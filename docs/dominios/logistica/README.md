# Domínio: logistica

**Status:** 🟡 Esqueleto criado — implementação pendente
**Pacote Java:** `com.cernecommerce...logistica`
**Rota HTTP base:** `/logistica`

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
