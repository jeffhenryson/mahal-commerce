# CRM — roteiro: cliente identificável no balcão e programa de cashback

**Criado em:** 2026-07-28, a partir do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Para quê:** dar a ordem de execução do backlog do CRM. O módulo tem 9 features entregues e
nenhuma documentação de domínio; o que entra agora é o que o PDV e o marketplace precisam dele.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista.

**Status em 2026-08-04:** este roteiro já cumpriu seu propósito — CRM-F003 (cashback, fechado
2026-07-29) e CRM-C002 (auditoria/permissão do export, fechado 2026-08-04) eram os dois itens que
o motivaram. O prompt abaixo está congelado no estado de 2026-07-28/29 e não reflete mais o
backlog atual (ex.: cita "a próxima migration é V70" — hoje é V82). Para o estado real, use
sempre o [backlog do README](README.md#backlog-do-módulo), não este arquivo.

---

## Prompt para colar numa sessão nova

```
Continue o desenvolvimento do módulo CRM do Mahal backend.

FONTE DA VERDADE
Backlog: docs/dominios/crm/README.md, seção "## Backlog do Módulo".
Desenho e justificativa: docs/plano-pdv-marketplace.md — leia §2.4 (cashback inteiro) e
§2.5 (cliente) antes de escrever qualquer linha.
docs/backlog.md é só índice — não adicione itens lá.

ESTADO ATUAL (revisado em 2026-07-29)
CRM-C005 e CRM-F002 estão FECHADOS: Customer não exige mais email — pelo menos um entre
cpf/email/contato basta, CPF é o identificador oficial, e GET /crm/customers/lookup existe.
Ver Histórico do README para o desenho final (mais amplo que este roteiro previa).
O que ainda falta:
- GET /crm/customers/{id}/orders e /cashback retornam List.of() direto no controller
  (CrmController.java:217,230) e CustomerResponseDTO.cashback é constante zero. São
  placeholders EXPLÍCITOS de F003, não bugs: a rota foi entregue sem fonte de dado.
- O README não tem Modelo de Domínio, Regras de Negócio, API, Schema nem Cobertura de
  Testes (CRM-C001) — as 9 features legadas F001-F009 nunca ganharam essa documentação.

ORDEM (revisada em 2026-07-29: CRM-C005 + CRM-F002 fechados)
 1. ~~CRM-C005 — email opcional, unicidade por CPF~~ ✅ Fechado 2026-07-29 — modelo final
    mais amplo que o previsto aqui: CPF é o identificador OFICIAL, email e contato são
    alternativos (qualquer um dos três basta), não só "email OU cpf". Ver Histórico do
    README.
 2. ~~CRM-F002 — lookup por CPF/contato + cadastro rápido~~ ✅ Fechado junto com a C005 —
    GET /crm/customers/lookup?cpf=&email=&contato= sob CRM_CUSTOMER_LOOKUP.
 3. CRM-F003 — programa de cashback (Fatia 4). O maior item do módulo, agora destravado.
    Depende da Fatia 3 (pagamento — FECHADA 2026-07-29, order_payment existe), porque o
    ganho é calculado sobre o líquido pago. Calibragem da taxa global fica para quando
    esta fatia começar de verdade — decisão de negócio adiada com o usuário.
 4. CRM-F001 — orders/cashback deixam de ser placeholder. Depende de PDV-F003 (a tabela
    sales_order, já existe) e de CRM-F003 (o ledger, ainda não).
 5. ~~CRM-C002 — auditoria no export da base~~ ✅ Fechado 2026-08-04 — rate limit, auditoria e
    permissão dedicada (`CRM_CUSTOMER_EXPORT`) entregues.
 6. CRM-C001 — auditar o código e completar o README. Por último.
 7. CRM-C003 e CRM-C004 — decisão sobre o disparo de campanha que não envia nada, e
    AuditEvent no ativar/desativar automação.

DECISÕES JÁ TOMADAS — não reabrir
- Cashback é LEDGER APPEND-ONLY (cashback_entry), nunca coluna de saldo. Saldo é SUM sobre
  as entradas liberadas. Nenhuma linha é atualizada depois de escrita; nenhuma é deletada.
  Um saldo mutável é impossível de auditar quando o cliente reclama. §2.4.
- Taxa é cadeia SKU → categoria → global, em UMA tabela (cashback_rate) com scope +
  scope_ref. A regra ativa mais específica vence. Taxa ZERO é significativa e diferente de
  "sem taxa definida" — por isso escopo é linha, não coluna anulável.
- A taxa aplicada é CARIMBADA no order_item (cashback_percent). Sem esse snapshot, mudar a
  taxa amanhã reescreve o valor gerado por pedidos de ontem.
- Base de cálculo: POR ITEM, sobre o líquido, cada item com sua taxa:
  (unit_price * quantity - discount_amount) * cashback_percent / 100.
- NÃO se ganha cashback sobre a parte paga com cashback. Sem essa regra o programa é uma
  máquina de imprimir crédito.
- Resgate NÃO é forma de pagamento: é uma entrada REDEEMED no ledger mais um desconto no
  pedido. Misturar com order_payment faria o DRE contar a mesma receita duas vezes.
- A carência tem que ser PELO MENOS tão longa quanto a janela de devolução — é a regra que
  faz o caso difícil (cliente resgata, depois devolve) simplesmente não existir. Validar no
  startup. Saldo negativo é PERMITIDO (é dívida do cliente, abatida do próximo ganho); não
  bloqueie a devolução por um problema contábil.
- GET /cashback/margin-impact entra JUNTO com o cadastro de taxas, não depois. Sem ele
  ninguém descobre o carvão a 8% (que come 44% da margem do item) antes do fechamento do
  mês. A matemática já existe: Pricing.marginPercent() e marginAmount().

COMO TRABALHAR
- Um item por vez. Ao terminar cada um, PARE e me mostre o resultado antes de seguir.
- TDD, na ordem domínio → service → persistência → controller → migration.
- NÃO execute ./mvnw. Não há JDK no WSL — eu rodo no PowerShell e reporto. Me entregue o
  comando pronto: ./mvnw test "-Dtest=ClasseA,ClasseB"
- NÃO faça commit. Eu commito manualmente; deixe no working tree.
- Ao concluir: remova o item do backlog do README, acrescente linha no Histórico, registre
  em docs/feature-registry.md, atualize docs/api-reference.md e a collection Postman.

ARMADILHAS DESTE PROJETO
- @DataJpaTest e @WebMvcTest NÃO existem no classpath (Spring Boot 4). Teste de repositório
  é @SpringBootTest + @ActiveProfiles("dev") + @Transactional com flush()+clear().
- HexagonalArchitectureTest barra framework em core/domain e core/ports; em core/service só
  libera org.springframework.transaction.*. ApplicationEventPublisher fica no controller.
- A próxima migration é V70 — V69 (CRM-C005) já está commitada.
- Permissão nova precisa de ON CONFLICT DO NOTHING na migration E de entrada em SeedConfig
  e DevRoleBootstrapConfig, senão dá 403 em dev.
- Todo endpoint novo precisa de @PreAuthorize.

Comece lendo o README do módulo e o §2.4 do plano, e me apresente o plano para CRM-F003 (cashback) — sem travar a taxa global ainda, isso é decisão do dono.
```

---

## Por que esta ordem

| Agrupamento | Razão |
|---|---|
| ~~C005 e F002 antes de tudo~~ ✅ | Fechado 2026-07-29. `CampaignAutomation`/`EmailPort` não precisaram de ajuste: `dispatchAutomation` não envia e-mail de verdade ainda (CRM-C003), então o risco cogitado aqui não se materializou. |
| F003 (cashback) depois da Fatia 3 (pagamento, no PDV) | O ganho é calculado sobre o **líquido efetivamente pago**. `order_payment` já existe (Fatia 3 fechada 2026-07-29) — a pergunta "quanto o cliente pagou" já é respondível. |
| F001 por último entre as features | Depende de duas coisas: a tabela `sales_order` (PDV-F003, ✅ existe) e o ledger (`CRM-F003`, ainda não). Fazer antes significa consultar tabela vazia. |
| ~~C002 fora da fila~~ ✅ | Fechado 2026-08-04. Era o maior risco de segurança aberto do módulo — `GET /crm/customers/export` devolvia nome, telefone, e-mail e CPF de toda a base sem rate limit, auditoria nem permissão dedicada. Os três foram entregues. |

## Riscos a considerar antes de encarar a lista

**O cashback é o maior item isolado do projeto (6–8 dias)** e o único cujo modelo é ponto de
não-retorno: se o saldo nascer como coluna mutável, o extrato do cliente nunca mais existe, e
quando ele reclamar não há resposta. Os *parâmetros* (taxas, carência, teto de resgate) são todos
configuráveis e reversíveis — errar neles custa um mês de margem, não o modelo.

**A calibragem de taxa é decisão de negócio, não de código.** O plano recomenda taxa ≈ 15% da
margem (≈2,5% em carvão, ≈10% em essência artesanal) em vez dos 8% fixos, porque 8% em carvão come
44% do lucro do item. Leve os números ao dono antes de semear a taxa global — a migration V69 do
plano semeia `GLOBAL 8%`, e esse default merece uma conversa.

## O que "módulo fechado" significa aqui

~~C005, F002, F003, C002, C004~~ ✅ todos fechados (F003 em 2026-07-29; C002 e a correção de doc
do C004 em 2026-08-04). O perfil 360 só falta `GET /crm/customers/{id}/orders` (CRM-F001, ainda
placeholder) para não mentir mais. C001 (documentar) e C003 (campanha que não envia mensagem) são
dívida antiga e não bloqueiam o PDV nem o marketplace.
