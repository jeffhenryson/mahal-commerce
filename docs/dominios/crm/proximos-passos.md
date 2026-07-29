# CRM — roteiro: cliente identificável no balcão e programa de cashback

**Criado em:** 2026-07-28, a partir do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Para quê:** dar a ordem de execução do backlog do CRM. O módulo tem 9 features entregues e
nenhuma documentação de domínio; o que entra agora é o que o PDV e o marketplace precisam dele.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista.

---

## Prompt para colar numa sessão nova

```
Continue o desenvolvimento do módulo CRM do Mahal backend.

FONTE DA VERDADE
Backlog: docs/dominios/crm/README.md, seção "## Backlog do Módulo".
Desenho e justificativa: docs/plano-pdv-marketplace.md — leia §2.4 (cashback inteiro) e
§2.5 (cliente) antes de escrever qualquer linha.
docs/backlog.md é só índice — não adicione itens lá.

ESTADO ATUAL
As 9 features da série legada F001–F009 foram entregues SEM que a documentação de domínio
fosse criada — o README não tem Modelo de Domínio, Regras de Negócio, API, Schema nem
Cobertura de Testes (CRM-C001). Duas coisas específicas travam o resto do projeto:
- Customer EXIGE email não-nulo e com formato válido (core/domain/model/crm/Customer.java
  :30-35). O cliente de balcão tem CPF e telefone e frequentemente não quer dar e-mail —
  identificá-lo hoje exige INVENTAR um e-mail, o que polui a base, quebra o disparo de
  campanha (CampaignAutomation) e destrói a unicidade por e-mail.
- GET /crm/customers/{id}/orders e /cashback retornam List.of() direto no controller
  (CrmController.java:217,230) e CustomerResponseDTO.cashback é constante zero. São
  placeholders EXPLÍCITOS de F003, não bugs: a rota foi entregue sem fonte de dado.

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
 5. CRM-C002 — auditoria no export da base. Independente de tudo acima; é o maior risco de
    segurança aberto do módulo e pode ser feito a qualquer momento.
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
| C005 antes de tudo no módulo | É pré-requisito de cadastro rápido no balcão, que é pré-requisito de cashback no balcão. Enquanto `email` for obrigatório, o operador precisa inventar um e-mail para identificar o cliente — e cada e-mail inventado é uma linha que polui a base para sempre. |
| C005 e F002 juntos | Formam a Fatia 2 do plano. O `CHECK` do schema e a unicidade por CPF só fazem sentido quando existe o fluxo que os exercita. |
| F003 (cashback) depois da Fatia 3 (pagamento, no PDV) | O ganho é calculado sobre o **líquido efetivamente pago**. Sem `order_payment`, "quanto o cliente pagou" não é uma pergunta respondível. |
| F001 por último entre as features | Depende de duas coisas que ainda não existem: a tabela `sales_order` (PDV-F003) e o ledger (`CRM-F003`). Fazer antes significa consultar tabelas vazias. |
| C002 fora da fila | Não depende de nada e é o maior risco de segurança aberto do módulo — `GET /crm/customers/export` devolve nome, telefone, e-mail e CPF de toda a base, em texto claro, sem auditoria. Pode ser feito em qualquer janela livre, inclusive antes da Fatia 0. |

## Riscos a considerar antes de encarar a lista

**CRM-C005 mexe numa invariante de domínio já testada.** Não é adicionar campo: é remover uma
obrigatoriedade da qual outros pontos podem depender silenciosamente. Antes de mudar, procure todo
uso de `Customer.email` — em particular `CampaignAutomation` e o `EmailPort`, porque um segmento de
campanha que assumia e-mail sempre presente passa a poder receber cliente sem e-mail. Decidir o que
acontece nesse caso (pular o cliente? falhar? mandar por WhatsApp?) faz parte do item.

**O cashback é o maior item isolado do projeto (6–8 dias)** e o único cujo modelo é ponto de
não-retorno: se o saldo nascer como coluna mutável, o extrato do cliente nunca mais existe, e
quando ele reclamar não há resposta. Os *parâmetros* (taxas, carência, teto de resgate) são todos
configuráveis e reversíveis — errar neles custa um mês de margem, não o modelo.

**A calibragem de taxa é decisão de negócio, não de código.** O plano recomenda taxa ≈ 15% da
margem (≈2,5% em carvão, ≈10% em essência artesanal) em vez dos 8% fixos, porque 8% em carvão come
44% do lucro do item. Leve os números ao dono antes de semear a taxa global — a migration V69 do
plano semeia `GLOBAL 8%`, e esse default merece uma conversa.

## O que "módulo fechado" significa aqui

Fechar C005, F002, F003, F001 e C002 — com isso o cliente é identificável no balcão, o cashback
roda de ponta a ponta e o perfil 360 deixa de mentir. C001 (documentar), C003 (campanha que não
envia) e C004 (auditoria na automação) são dívida antiga do módulo e não bloqueiam o PDV nem o
marketplace.
