# Financeiro — roteiro: o que ele espera dos outros módulos

**Criado em:** 2026-07-28, a partir do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Para quê:** registrar o que o financeiro precisa que seja decidido **agora** por outros módulos,
mesmo sem uma linha de código fiscal ou contábil ser escrita — porque essas decisões são caras de
reverter depois.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista.

> O financeiro é o módulo mais **dependente** do projeto: quase tudo que ele precisa nasce em
> `vendas-balcao` (pedido, pagamento), `crm` (cashback) e `estoque` (custo). Ele é o último a ser
> construído e o primeiro a ser prejudicado se os outros modelarem errado.

---

## O que precisa ser decidido agora, mesmo sem construir nada

Estas três decisões são de **outros módulos**, mas o custo de errar aparece aqui. Elas estão no
plano e precisam ser cobradas de quem implementar as Fatias 0 e 3.

| Decisão | Onde é implementada | Por que o financeiro depende |
|---|---|---|
| **Pedido imutável depois de concluído, com numeração estável e sem buracos** | PDV-F003 (Fatia 0) | `order_number` vem de sequência própria e é emitido na **conclusão**. `BIGSERIAL` deixa buracos em rollback de transação, e buraco em numeração de documento fiscal é problema com o fisco. Adicionar NCM/CEST ao produto depois é migration trivial; retrofitar imutabilidade e numeração num modelo já em produção, não. |
| **`cost_price` congelado no item do pedido** | PDV-F004 (Fatia 0) | Sem o snapshot, a próxima compra que mudar o `costPrice` do produto reescreve a margem histórica de **todos** os pedidos passados. Não há como reconstruir: o custo antigo não está em lugar nenhum. É a base de qualquer DRE por período. |
| **Troco não é linha de pagamento** | PDV-F006 (Fatia 3) | Troco é `change_amount` no pedido, derivado de `soma dos pagamentos em DINHEIRO − total` quando positivo. Modelar troco como pagamento negativo faz todo `SUM(amount)` mentir — e o erro só é descoberto quando não bate com o extrato bancário. |

E uma que é **deste** módulo, mas precisa ser dita antes do cashback existir:

**Cashback é provisão de passivo reconhecida no ganho, não despesa no resgate** (`FIN-F001`). O
crédito já existe contra a empresa no momento em que é gerado. Reconhecê-lo só no resgate infla o
resultado dos meses em que os clientes acumulam e afunda o mês em que resgatam. Também por isso o
resgate **não** é forma de pagamento: seria a mesma receita contada duas vezes.

---

## Prompt para colar numa sessão nova

```
Continue o desenvolvimento do módulo FINANCEIRO do Mahal backend.

ANTES DE COMEÇAR — confirme comigo o estado das Fatias 0, 3 e 4 do plano (pedido unificado,
pagamento, cashback). O financeiro consome as três; sem elas não há o que faturar, conciliar
nem provisionar, e o certo é me dizer isso em vez de construir sobre tabelas vazias.

FONTE DA VERDADE
Backlog: docs/dominios/financeiro/README.md, seção "## Backlog do Módulo".
Roteiro e dependências: docs/dominios/financeiro/proximos-passos.md.
Desenho: docs/plano-pdv-marketplace.md — leia §2.6 (pagamento) e §2.8 (fiscal).

ESTADO ATUAL
O módulo é um ESQUELETO: README no molde, sem Modelo de Domínio, Regras, API, Schema nem
Testes (FIN-C001). Não existe DRE, conciliação, fluxo de caixa nem nada fiscal.

ORDEM
 1. FIN-F001 — cashback como provisão de passivo no DRE, reconhecida no ganho e não no
    resgate. Consome o ledger cashback_entry (CRM-F003).
 2. FIN-F002 — NFC-e via emissor terceiro (Fatia 11). Ver a regra abaixo.
 3. FIN-C001 — auditar e documentar o módulo no padrão de estoque.

DECISÕES JÁ TOMADAS — não reabrir
- NUNCA construir emissão fiscal própria. Em nenhum horizonte deste projeto. Focus NFe ou
  PlugNotas, por ordem de R$100–300/mês. Construir significa: schemas XML por UF, assinatura
  com certificado A1/A3, contingência offline, janela de cancelamento, eventos de
  manifestação, homologação na SEFAZ e manutenção perpétua a cada mudança de layout — meses
  de time para entregar algo pior que o produto de prateleira. É o erro mais caro do plano
  inteiro. §2.8 e §8.1.
- Para tabacaria o argumento é mais forte: fumo e derivados têm ICMS-ST (recolhido antes,
  pelo fabricante) e IPI; a nota do varejista sai com CST 60 e NCM/CEST específicos por
  produto. Errar essa configuração não gera bug, gera AUTUAÇÃO — e é exatamente o tipo de
  coisa que o emissor terceiro trata como configuração por produto.
- A NFC-e NÃO é bloqueante para o PDV rodar. A loja hoje já emite nota por outro meio; o PDV
  roda em paralelo enquanto a operação amadurece. A integração é o portão para DESLIGAR o
  processo antigo, não para ligar o PDV.
- Troco é change_amount no pedido, nunca linha de pagamento negativa.
- Cashback é provisão reconhecida no ganho; resgate não é forma de pagamento.
- EST-F007 (custo médio ponderado), que destrava o DRE, NÃO deve ser feito antes do cashback
  (Fatia 4): o costPrice manual de V63 já entrega a ordem de grandeza, e é a ordem de
  grandeza que decide se a taxa do carvão é 2% ou 8%. Depois do cashback rodando, o custo
  médio refina; antes, ele atrasa. §8.9.

COMO TRABALHAR
- Um item por vez. Ao terminar cada um, PARE e me mostre o resultado.
- NÃO execute ./mvnw (sem JDK no WSL). Me entregue o comando pronto.
- NÃO faça commit.

Comece confirmando o estado das Fatias 0, 3 e 4.
```

---

## Riscos a considerar

**O maior risco deste módulo é ele ser construído tarde demais para influenciar quem o alimenta.**
As três decisões da tabela acima acontecem em `vendas-balcao`, meses antes de alguém escrever a
primeira linha de DRE. Se ninguém as cobrar na Fatia 0, o financeiro nasce sem margem histórica
recuperável e com numeração fiscal furada — e as duas coisas são irreversíveis em dado já gravado.

**A NFC-e tem calendário maior que o esforço.** Certificado digital, cadastro fiscal da empresa e
homologação no emissor não comprimem com mais desenvolvedor. O esforço de código é de 5–8 dias; o
calendário pode ser de semanas. Comece a parte burocrática cedo, mesmo com a Fatia 11 longe.

## O que "módulo entregue" significa aqui

Fechar `FIN-F001` (provisão de cashback), `FIN-F002` (NFC-e via emissor) e `FIN-C001` (documentar),
mais o DRE propriamente dito — que depende de `EST-F007` (custo médio) e ainda não tem item de
backlog próprio. Quando as Fatias 0 a 4 estiverem prontas, vale rodar `/1-analise financeiro` para
levantar o backlog real do módulo, que hoje é só o esqueleto herdado.
