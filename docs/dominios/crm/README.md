# Domínio: crm

**Status:** 🟢 Operacional — roadmap inicial `F001–F009` entregue por completo, mais cliente
identificável no balcão sem e-mail (CRM-C005/F002, 2026-07-29)
**Pacote Java:** `com.cernecommerce.core.domain.model.crm`
**Rota HTTP base:** `/crm`
**Última atualização deste doc:** 2026-07-29 (CRM-C005/F002)

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

## Segurança e Infraestrutura

> Mecanismos transversais em [`docs/security.md`](../../security.md); ambientes e containers em
> [`docs/infrastructure.md`](../../infrastructure.md); o modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura). Aqui fica só o recorte
> deste domínio.

Este é o domínio com **mais dado pessoal** do backend — nome, telefone, e-mail e CPF de clientes
reais. As decisões abaixo pesam mais aqui do que em qualquer outro módulo.

### Permissões RBAC

Só duas permissões cobrem os **28 endpoints** do `CrmController`, criadas na V48 (com
`ON CONFLICT DO NOTHING`) e concedidas a `ROLE_ADMIN`; semeadas em `dev` por `SeedConfig` e
`DevRoleBootstrapConfig`.

| Permissão | Libera | Endpoints |
|---|---|---|
| `CRM_CUSTOMER_READ` | toda leitura: clientes, notas, pedidos, cashback, histórico de estágio, dashboard, tags, automações, log de disparos, status de canal e **o export CSV** | 13 |
| `CRM_CUSTOMER_MANAGE` | toda escrita: criar cliente e nota, mover estágio, CRUD de tags e associações, CRUD de automações e disparo manual | 11 |
| `CRM_CUSTOMER_LOOKUP` | `GET /crm/customers/lookup` — busca pontual por CPF/email/contato (CRM-F002) | 1 |

**Granularidade ainda insuficiente para o resto:** não há como separar "consultar um cliente" de
"exportar a base inteira", nem "criar nota" de "disparar campanha". `CRM_CUSTOMER_LOOKUP` resolveu
só o caso do balcão — quem atende ali agora pode achar **um** cliente por CPF/email/contato sem
precisar de `CRM_CUSTOMER_READ` (que levaria o export completo com CPF de brinde) —, mas o resto
do módulo continua com a granularidade grossa de sempre.

> Os nomes `CUSTOMER_CREATE`/`CUSTOMER_READ` que aparecem na descrição legada de F001 no
> [Histórico](#histórico-de-implementações) nunca existiram no código — as permissões reais
> sempre foram `CRM_CUSTOMER_*`.

### Rate limiting

❌ Nenhum endpoint deste módulo é limitado — o `LoginRateLimitingFilter`
(`infra/security/LoginRateLimitingFilter.java:42-77`) cobre apenas `/auth/**` e duas rotas de
notificação.

O caso mais sensível é `GET /crm/customers/export`: devolve a **base inteira sem paginação**
(`listCustomersForExport`), monta o CSV em memória e não deixa registro de auditoria. Um token
com `CRM_CUSTOMER_READ` pode baixar toda a base de clientes, repetidamente, sem nenhum freio nem
rastro. Ver **CRM-C002** e PLAT-C030.

### Isolamento de dados e dado pessoal

Single-tenant, sem carteira por vendedor: quem tem `CRM_CUSTOMER_READ` vê **todos** os clientes.

`customers` (V48) guarda `nome`, `contato`, `email` e `cpf` — **tudo em texto claro**, sem
criptografia em repouso nem mascaramento na API ou no CSV. Não há registro de consentimento,
anonimização, exportação por titular nem exclusão de cliente (o domínio não tem endpoint de
delete). Sobre LGPD no backend como um todo, ver
[`plataforma`](../plataforma/README.md#conformidade-lgpd).

### Auditoria

O `CrmController` publica `AuditEvent` em 10 operações de escrita:

| Operação | `EventType` |
|---|---|
| `POST /crm/customers` | `CUSTOMER_CREATED` |
| `POST /crm/customers/{id}/notes` | `CUSTOMER_NOTE_ADDED` |
| `PATCH /crm/customers/{id}/estagio` | `CUSTOMER_STAGE_CHANGED` |
| `POST` / `DELETE /crm/tags` | `TAG_CREATED` / `TAG_DELETED` |
| `POST` / `DELETE /crm/customers/{id}/tags` | `CUSTOMER_TAG_ADDED` / `CUSTOMER_TAG_REMOVED` |
| `POST` / `DELETE /crm/automacoes` | `CAMPAIGN_AUTOMATION_CREATED` / `CAMPAIGN_AUTOMATION_DELETED` |
| `POST /crm/automacoes/{id}/disparar` | `CAMPAIGN_AUTOMATION_DISPATCHED` |

É a melhor cobertura de auditoria entre os domínios de negócio. **Lacunas:**
`PATCH /crm/automacoes/{id}/ativa` (ativar/desativar campanha) não gera evento, e nenhuma
leitura gera — inclusive o export CSV, que é justamente a leitura que deveria deixar rastro
(**CRM-C002**).

A trilha de movimentação no kanban tem registro próprio de domínio em
`customer_stage_transitions` (V50), independente dos `audit_logs`.

### Infraestrutura utilizada

| Recurso | Uso neste módulo | Se cair |
|---|---|---|
| Postgres 16 (H2 em `dev`) | `customers` (V48), `customer_notes` (V49), `customer_stage_transitions` (V50), `tags` + `customer_tags` (V51), `campaign_automations` + `campaign_log` (V52) | módulo indisponível |
| Cache de authorities (Redis/Caffeine, TTL 60s) | checagem de `@PreAuthorize` | latência maior |
| `EmailPort` (Resend em `hml`/`prod`, `logging` em `dev`) | **apenas** o status do canal em `GET /crm/canais/status` | badge de canal reporta desconectado |

Sem fila, sem storage de arquivo e **sem integração de WhatsApp** — `getChannelStatus` devolve
`conectado = false` com o texto fixo "Integração de WhatsApp ainda não implementada".

⚠️ **`POST /crm/automacoes/{id}/disparar` não envia nada.** `CrmService.dispatchAutomation`
busca os clientes do segmento-alvo e grava uma linha em `campaign_log` por cliente — nenhuma
mensagem sai por e-mail ou WhatsApp. O `EmailPort` injetado no service só é usado para o status
do canal.

### Limites operacionais

- `GET /crm/customers`: paginado, com filtro por `search` e segmento.
- `GET /crm/customers/export`: **não paginado** — a base inteira em memória, com BOM UTF-8 e
  `Content-Disposition: attachment; filename="clientes.csv"`.
- `POST /crm/automacoes/{id}/disparar`: sem teto de destinatários; uma linha de log por cliente
  do segmento, tudo em uma transação.
- Validação: `@Valid` nos requests (e-mail, tamanhos, enums), com erros no formato `ApiError`.

### Riscos conhecidos

- **CRM-C002** — export da base sem auditoria, sem paginação e sem rate limit.
- **CRM-C001** — README ainda sem Modelo de Domínio, Regras, API, Schema e Testes.
- **PLAT-C030** — sem rate limit em endpoint de negócio.

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
| CRM-F001 | 🔴 Alta | Feature | perfil-360-deixa-de-ser-placeholder | `GET /crm/customers/{id}/orders` e `/cashback` retornam `List.of()` no controller (`CrmController.java:217,230`) e `CustomerResponseDTO.cashback` é constante zero — F003 entregou as rotas sem fonte de dado. Passam a consultar `sales_order` e o ledger de cashback. Depende de PDV-F003 (pedido unificado) e CRM-F003. [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §5.2. | Pendente |
| CRM-F003 | 🔴 Alta | Feature | programa-de-cashback | Taxas por escopo `GLOBAL`/`CATEGORY`/`SKU` (a mais específica ativa vence) e ledger `cashback_entry` **append-only** — saldo é `SUM`, nunca coluna mutável. Ganho na conclusão do pedido com carência, expiração por scheduler e estorno via `REVERSED`. Resgate é desconto no pedido, **não** forma de pagamento. Inclui `GET /cashback/margin-impact`, que aponta os produtos cuja taxa consome margem demais — sem ele ninguém descobre o carvão a 8% antes do fechamento do mês. Migration V68. Fatia 4, §2.4. | Pendente |
| CRM-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | Este README não tem Modelo de Domínio, Regras de Negócio, API, Schema nem Cobertura de Testes — as 9 features foram entregues sem que a documentação de domínio fosse criada. Auditar o código e preencher no padrão de `estoque`. | Pendente |
| CRM-C002 | 🔴 Alta | Correção | export-da-base-sem-auditoria-nem-limite | `GET /crm/customers/export` (`CrmController.java:150`) devolve **toda** a base de clientes — nome, telefone, e-mail e CPF em texto claro — sem paginação, sem rate limit e **sem publicar `AuditEvent`**. Qualquer token com `CRM_CUSTOMER_READ` (a permissão de leitura mais básica do módulo) pode drenar a base em loop sem deixar rastro. No mínimo: publicar evento de auditoria no export, e avaliar permissão dedicada + limite. O rate limit em si é transversal (PLAT-C030). | Pendente |
| CRM-C003 | 🟢 Melhoria | Correção | disparo-de-campanha-nao-envia-nada | `CrmService.dispatchAutomation` (linha 215) grava uma linha em `campaign_log` por cliente do segmento e **não envia mensagem alguma** — o `EmailPort` injetado só serve ao `getChannelStatus`. A tela de Automações reporta disparo bem-sucedido para um envio que nunca aconteceu. Ou o envio é implementado, ou a resposta/documentação precisa deixar claro que é simulação. | Pendente |
| CRM-C004 | 🟢 Melhoria | Correção | audit-event-ausente-em-ativar-desativar-automacao | `PATCH /crm/automacoes/{id}/ativa` é a única escrita do módulo sem `AuditEvent` — ligar ou desligar uma campanha não deixa rastro, enquanto criar e apagar deixam. | Pendente |

Novas features e correções do CRM seguem as séries `CRM-F001+` e `CRM-C002+`. A série legada
`F001–F009` está congelada (todos concluídos, ver histórico).

## Histórico de Implementações

- **2026-07-29** — `cliente-identificavel-no-balcao-sem-email` (**CRM-C005** + **CRM-F002**):
  `Customer.email` deixou de ser obrigatório. Modelo revisto com o dono, mais amplo que o §2.5
  original do plano (que só prometia "email OU cpf"): **CPF é o identificador oficial** do
  cadastro; email e contato (telefone) são identificadores alternativos, qualquer um dos três
  basta — checado no compact constructor de `Customer`, não em Bean Validation, porque é regra
  de negócio entre campos. Venda anônima de balcão continua **sem** exigir nenhum dos três; o
  gatilho para identificar é o operador querer o "CPF na nota?", não a venda em si. Cliente sem
  CPF ("cliente leve", achado só por email/contato) é válido e pesquisável, mas fica marcado como
  não elegível a cashback via `Customer.isOfficiallyRegistered()` — porta pronta para a Fatia 4
  usar, sem reimplementar a checagem. Migration V69: `email`/`contato` viram `NULLABLE`,
  `uk_customers_cpf` novo (UNIQUE padrão — SQL trata cada NULL como distinto dos demais, então não
  precisou de índice parcial, que o H2 do perfil dev não suporta), `CHECK` exigindo pelo menos um
  identificador. Novo `GET /crm/customers/lookup?cpf=&email=&contato=` sob `CRM_CUSTOMER_LOOKUP`
  (permissão própria, separada de `CRM_CUSTOMER_READ` — achar um cliente não é listar a base),
  prioridade cpf → email → contato quando mais de um critério vier preenchido; 404 quando não
  acha, 400 quando nenhum critério é informado. `POST /crm/customers` (cadastro rápido) reaproveitado
  sem mudança de rota — só `contato`/`email` deixaram de ser obrigatórios no request. Duplicidade
  de CPF passa a responder `409 CUSTOMER_CPF_ALREADY_EXISTS`, espelhando o que já existia para
  email. Coberto por casos novos em `CustomerTest`, `CrmServiceTest`, `CrmControllerTest` e
  `CrmControllerSecurityTest` (inclusive uma jornada de ponta a ponta: cadastra por CPF, acha pelo
  lookup).

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

Roteiro completo, com prompt pronto para colar numa sessão nova, em
[`proximos-passos.md`](proximos-passos.md).

- [ ] **CRM-C002** — auditoria no export da base; é o maior risco aberto do módulo.
- [x] **CRM-C005 + CRM-F002** — Fatia 2 de [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md):
      cliente identificável no balcão. Fechado em 2026-07-29 — destrava a Fatia 4.
- [ ] **CRM-F003** — Fatia 4: programa de cashback. É o pedido nominal do dono. Calibragem da taxa
      global fica para quando esta fatia começar de verdade (decisão adiada com o usuário).
- [ ] **CRM-C001** — auditar o código e completar este README (Modelo de Domínio, Regras, API, Schema, Testes).
- [ ] **CRM-C003** — decidir entre implementar o envio de campanha ou explicitar que é simulação.
