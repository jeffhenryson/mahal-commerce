# Domínio: financeiro

**Status:** 🟡 Esqueleto criado — implementação pendente
**Pacote Java:** `com.cernecommerce...financeiro`
**Rota HTTP base:** `/financeiro`
**Última atualização deste doc:** 2026-07-27 (seção de Segurança e Infraestrutura)

## Objetivo

Gestão financeira integrada do lounge físico e do e-commerce.

## Escopo planejado

- **DRE simplificado:** apuração de resultado por período (`DreLine`).
- **Fluxo de caixa:** lançamentos de entrada/saída (`CashFlowEntry`).
- **Conciliação de taxas de gateways:** repasse × venda, taxas por gateway
  (`GatewayFee` + `Reconciliation`).

## Estrutura hexagonal (criada)

| Camada | Artefato |
|---|---|
| domain/model | `core/domain/model/financeiro/CashFlowEntry` |
| ports/in | `core/ports/in/FinanceiroUseCase` |
| ports/out | `core/ports/out/financeiro/LedgerRepository` |
| service | `core/service/FinanceiroService` (stub, wired em `CoreBeanConfig`) |
| adapter/in | `adapter/in/controller/FinanceiroController` → `GET /financeiro/cash-flow?page&size` (stub, retorna `PageResult<CashFlowEntry>` vazio) |

## Segurança e Infraestrutura

> Transversal em [`docs/security.md`](../../security.md) e
> [`docs/infrastructure.md`](../../infrastructure.md); modelo RBAC completo em
> [`plataforma`](../plataforma/README.md#segurança-e-infraestrutura).

**O que já existe.** `FINANCEIRO_READ` (criada na V53 com `ON CONFLICT DO NOTHING`, concedida a
`ROLE_ADMIN`; semeada em `dev` por `SeedConfig`/`DevRoleBootstrapConfig`) protege o único
endpoint do módulo, `GET /financeiro/cash-flow`. O `@PreAuthorize` foi acrescentado em **C004**,
que tirou os controllers stub do fallback genérico `anyRequest().authenticated()`. Não há tabela,
auditoria nem infra própria — o service devolve página vazia.

**O que este módulo vai precisar quando sair do esqueleto.** É o domínio de maior sensibilidade
do sistema: consolida resultado, custo e repasse de gateway.

- [ ] Permissões separando leitura de DRE, lançamento manual e conciliação — um único `*_READ`
      não serve para dado financeiro consolidado.
- [ ] `AuditEvent` em **toda** escrita (lançamento, ajuste, conciliação). Hoje `compras` e
      `vendas-balcao`, que são as origens do dado financeiro, não publicam evento algum
      (`COM-C003`, `PDV-C003`) — a trilha precisa existir antes de o financeiro consumi-los.
- [ ] Imutabilidade dos lançamentos: estorno como novo lançamento, nunca `UPDATE`/`DELETE`, no
      mesmo espírito do ledger `stock_movement` do [`estoque`](../estoque/README.md).
- [ ] Rate limit nos relatórios agregados, que serão as consultas mais caras do backend
      (PLAT-C030).
- [ ] Credenciais de gateway no fluxo de `.env` + validadores de startup
      ([`docs/infrastructure.md`](../../infrastructure.md#variáveis-de-ambiente-e-segredos)) e
      importação de repasses com verificação de origem.
- [ ] Retenção fiscal própria: os `audit_logs` expiram em 365 dias
      (`AuditLogCleanupService`), prazo insuficiente para obrigação contábil.

## Testes no Postman

Coleção do módulo: [`financeiro.postman_collection.json`](financeiro.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/financeiro/financeiro.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

A coleção valida o que já existe (`GET /financeiro/cash-flow`): o contrato de `PageResult`, a
validação de `page`/`size` e a proteção por `FINANCEIRO_READ` — e serve de esqueleto para
crescer junto com o módulo.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| FIN-F001 | 🟡 Média | Feature | cashback-como-provisao-no-dre | Cashback reconhecido como **provisão de passivo no ganho**, não como despesa no resgate — o crédito já existe contra a empresa no momento em que é gerado, e reconhecê-lo só no resgate infla o resultado dos meses em que os clientes acumulam. Consome o ledger `cashback_entry` (CRM-F003). Ver [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.4. | Pendente |
| FIN-F002 | 🟡 Média | Feature | nfce-via-emissor-terceiro | Port fiscal + adapter para Focus NFe ou PlugNotas, campos fiscais (NCM/CEST/CFOP) no produto, emissão na conclusão do pedido e cancelamento dentro da janela legal. **Nunca construir emissão própria** (§2.8/§8.1): schemas XML por UF, certificado A1/A3, contingência, homologação SEFAZ e manutenção perpétua, contra ~R$100–300/mês de prateleira. Para tabacaria o argumento é mais forte ainda — fumo tem ICMS-ST e IPI, e errar CST 60/NCM/CEST não gera bug, gera autuação. **Não é bloqueante para o PDV rodar**; é o portão para desligar o processo fiscal atual. Fatia 11 — calendário (certificado, cadastro fiscal, homologação) maior que o esforço. | Pendente |
| FIN-C001 | 🟡 Importante | Correção | auditar-e-documentar-o-modulo | README ainda no molde de esqueleto: faltam Modelo de Domínio, Regras de Negócio, API, Schema e Cobertura de Testes. Rode `/1-analise financeiro`. Padrão: [`estoque`](../estoque/README.md). | Pendente |

> `EST-F007` (`valorizacao-custo-medio`, que alimenta o DRE) é um cruzamento
> `estoque↔financeiro` e está rastreado em [`estoque`](../estoque/README.md#backlog-do-módulo).

> O que o financeiro precisa que seja decidido **agora**, mesmo sem construir nada de fiscal
> ([`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §2.8): o pedido nasce **imutável
> depois de concluído** e com **numeração estável e sem buracos** (`order_number` de sequência
> própria, emitido na conclusão — `BIGSERIAL` deixa buracos em rollback, e buraco em numeração de
> documento fiscal é problema com o fisco). Adicionar NCM/CEST depois é migration trivial;
> retrofitar imutabilidade e numeração num modelo já em produção, não.

## Próximos passos

Roteiro completo em [`proximos-passos.md`](proximos-passos.md) — inclui **as três decisões de
outros módulos que precisam ser cobradas agora** para o financeiro não nascer sem margem
histórica nem numeração fiscal confiável.

- [ ] **FIN-F001** — cashback como provisão de passivo, reconhecida no ganho.
- [ ] **FIN-F002** — NFC-e via emissor terceiro; nunca construir emissão própria.
- [ ] Modelos: `DreLine`, `GatewayFee`, `Reconciliation`.
- [ ] Casos de uso: `buildDre`, `reconcileGatewayFees`, consulta de fluxo de caixa por período.
- [ ] Ports out: `GatewayFeeReconciliationPort` (importação de repasses/taxas dos gateways).
- [ ] Adapter de persistência: entities JPA + repository impl + migration Flyway.
- [ ] Permissões RBAC do domínio + `@PreAuthorize` nos endpoints.
