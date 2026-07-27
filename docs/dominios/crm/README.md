# Domínio: crm

**Status:** 🟢 Operacional — roadmap inicial `F001–F009` entregue por completo
**Pacote Java:** `com.cernecommerce.core.domain.model.crm`
**Rota HTTP base:** `/crm`
**Última atualização deste doc:** 2026-07-26

> ⚠️ **Este README ainda não passou por auditoria de código.** Ele foi criado em 2026-07-26 na
> descentralização do `docs/backlog.md` para receber os itens `F001–F009`, que estavam órfãos.
> As seções de Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes ainda
> precisam ser preenchidas a partir do código — rode `/1-analise crm` ou `/analyze-domain crm`.
> O padrão de README completo está em [`estoque`](../estoque/README.md).

## Objetivo

Gestão do relacionamento com clientes da tabacaria: base de clientes com segmentação RFM,
perfil 360º (pedidos, cashback, notas), funil de atendimento em kanban, campanhas de automação
por WhatsApp/e-mail e tags.

É o domínio com mais features entregues do backend, e o que mais destrava telas do frontend
`mahal-admin-ui`.

## Escopo planejado

- **Cadastro e base de clientes:** `Customer` com contato, CPF, origem e LTV. ✅ Implementado (F001, F002).
- **Segmentação RFM:** VIP / Recorrente / Novo / Inativo / Em Risco. ✅ Implementado (F002).
- **Perfil 360º:** histórico de pedidos, extrato de cashback, notas/interações. ✅ Implementado (F003).
- **Funil de atendimento:** kanban com trilha de auditoria das transições de estágio. ✅ Implementado (F004).
- **Dashboard:** agregações de clientes ativos, LTV médio, disparos e contagem por segmento. ✅ Implementado (F005).
- **Automações e campanhas:** regras por gatilho/segmento/canal com template e log de envios. ✅ Implementado (F006).
- **Tags e exportação.** ✅ Implementado (F007, F009).
- **Status de canal de envio.** ✅ Implementado (F008).

## Testes no Postman

Coleção do módulo: [`crm.postman_collection.json`](crm.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/crm/crm.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Clientes` | criação, busca por id, listagem com `search`, export CSV (confere BOM UTF-8 e `Content-Disposition`), 409 de e-mail duplicado, 400 de e-mail inválido e 404 |
| `02 — Notas` | criação (autor vem do token) e listagem |
| `03 — Kanban de estágios` | movimentação de estágio, trilha de transições e os 400 de estágio repetido e de valor fora do enum |
| `04 — Tags` | criação, associação ao cliente, listagem com `clientesCount`, remoção da associação, 409 de nome duplicado, 404 de tag inexistente e exclusão |
| `05 — Automações` | criação, listagem, disparo manual, log de disparos, ativar/desativar, 404 e exclusão |
| `06 — Dashboard e canais` | overview agregado e status dos canais de envio |
| `07 — Placeholders` | `orders` e `cashback`, que hoje sempre devolvem lista vazia |
| `08 — Segurança` | 401 sem token |

Cliente, tag e automação são criados com sufixo de timestamp; tag e automação são apagadas ao
final. O cliente permanece — o domínio não tem endpoint de exclusão.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| CRM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Este README não tem Modelo de Domínio, Regras de Negócio, API, Schema nem Cobertura de Testes — as 9 features foram entregues sem que a documentação de domínio fosse criada. Auditar o código e preencher no padrão de `estoque`. | Pendente |

Novas features e correções do CRM seguem as séries `CRM-F001+` e `CRM-C002+`. A série legada
`F001–F009` está congelada (todos concluídos, ver histórico).

## Histórico de Implementações

Série legada `F001–F009`, geração de planejamento de 2026-07-20. Descrições técnicas detalhadas
em [`docs/feature-registry.md`](../../feature-registry.md), seção `crm`.

| ID | Prioridade | Feature | Descrição | Tela destravada no frontend |
|---|---|---|---|---|
| F001 | 🔴 Alta | `cadastro-cliente` | Entidade `Customer` (nome, contato, email, CPF, origem, cadastradoEm), casos de uso de criar/buscar, permissões RBAC (`CUSTOMER_CREATE`, `CUSTOMER_READ`, …) e migration inicial. Fundação do módulo. | Dialog "Novo Cliente"; troca do guard `placeholder: true` de `/app/crm` pela permissão real `CUSTOMER_READ` |
| F002 | 🔴 Alta | `listagem-clientes-rfm` | Endpoint paginado e filtrável (busca por nome/telefone, filtro por segmento) devolvendo LTV, cashback, tags e segmento RFM calculado. | Base de Clientes |
| F003 | 🔴 Alta | `perfil-cliente-360` | Endpoints de histórico de pedidos, extrato de cashback e notas/interações por cliente. | Detalhe do Cliente (abas Visão Geral / Pedidos / Cashback / Notas) |
| F004 | 🟡 Média | `kanban-segmentacao` | Movimentação de cliente entre estágios/segmentos com trilha de auditoria da transição. | Kanban (Funil de Atendimento) |
| F005 | 🟡 Média | `dashboard-overview` | Agregação: total de clientes, ativos (30d), LTV médio, disparos WhatsApp/mês, contagem por segmento RFM. | Overview / Dashboard do CRM |
| F006 | 🟡 Média | `automacoes-campanhas` | CRUD de regras de automação (gatilho, segmento-alvo, canal WhatsApp/E-mail/ambos, template com placeholders `{nome}`/`{saldo}`, ativa/inativa) + log de envios e conversão. | Automações |
| F007 | 🟢 Baixa | `tags-segmentos` | CRUD de tags, associação cliente↔tag e contagem de clientes por tag. | Segmentos / Tags |
| F008 | 🟢 Baixa | `integracao-canal-envio` | Status real de conexão do canal de envio, substituindo o badge fixo "API WhatsApp: Conectada" hardcoded no frontend; reaproveita `EmailAdapterConfig`/`MailpitEmailAdapter` como base do canal de e-mail. | Badge de status em Automações |
| F009 | 🟢 Baixa | `exportacao-csv-clientes` | Exportação server-side da listagem (antes o CSV era montado no cliente a partir dos dados em memória). | Botão "Exportar CSV" na Base de Clientes |

## Próximos passos

- [ ] **CRM-C001** — auditar o código e completar este README (Modelo de Domínio, Regras, API, Schema, Testes).
