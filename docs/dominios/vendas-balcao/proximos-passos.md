# Vendas-balcão (PDV) — roteiro para tornar o módulo operável

**Criado em:** 2026-07-28, logo depois do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Para quê:** dar a ordem de execução do backlog do PDV até a loja poder operar o caixa pelo
sistema, sem `INSERT` manual e sem preço vindo do cliente HTTP.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista. O desenho de cada decisão (o porquê, a alternativa descartada e o custo
de errar) está no plano; aqui fica só a ordem e as armadilhas.

Este é o **módulo por onde o projeto começa**: as Fatias 0, 1 e 3 do plano são todas daqui.

> **Atualizado em 2026-07-29:** Fatias 0, 1 e 3 estão fechadas e com suíte completa verde (1442
> testes). Fatia 1 saiu com escopo ampliado — inclui a superfície `/orders` do administrador e a
> liquidação no balcão de pedido feito no app; detalhamento em
> [`fatia-1-ciclo-de-caixa.md`](fatia-1-ciclo-de-caixa.md). Fatia 3 (PDV-F006, pagamento com
> múltiplas formas, troco e comprovante interno) está registrada no
> [Histórico do README](README.md#histórico-de-implementações). **Próximo item do módulo:**
> `PDV-C001` (auditar e documentar) — o resto da fila de prioridade do PDV/CRM/cashback está em
> [`docs/plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md) §6 (Fatia 2 → CRM é a próxima
> peça fora deste módulo).

---

## Prompt para colar numa sessão nova

```
Continue o desenvolvimento do módulo VENDAS-BALCAO (PDV) do Mahal backend.

FONTE DA VERDADE
Backlog: docs/dominios/vendas-balcao/README.md, seção "## Backlog do Módulo".
Desenho e justificativa: docs/plano-pdv-marketplace.md — leia §1.2, §2.1, §2.3, §2.7 e §3
antes de escrever qualquer linha. Ele já decidiu as questões difíceis; não as reabra sem
motivo novo.
docs/backlog.md é só índice — não adicione itens lá.

ESTADO ATUAL
O módulo tem UMA feature entregue (EST-F010, baixa automática na venda) e é o mais raso do
projeto. Hoje:
- Sale/SaleItem não têm cliente, pagamento, desconto, status nem cancelamento. Não é
  "faltam campos": é uma entidade que não representa um pedido.
- SaleRepository (core/ports/out/pdv/SaleRepository.java:8-11) expõe SÓ save(). A venda é
  write-only: não existe GET /pdv/sales, não há como reler uma venda pela API.
- SaleItemRequest.unitPrice é o preço DIGITADO pelo cliente HTTP. Com Pricing existindo
  (V63), quem tem PDV_SALE_MANAGE vende qualquer coisa por qualquer preço sem trilha.
- CashRegisterSession é um record sem invariantes, sem fábricas e sem comportamento —
  abrir caixa exige INSERT manual no banco.
- PdvService.registerSale (:40-44) valida que a sessão está OPEN, mas NÃO que ela pertence
  a quem está vendendo.
- CashRegisterRepository.findOpenByOperator (:15) está implementado e nunca é chamado.
  Aproveite, não recrie.

ORDEM (as três primeiras fatias do plano são deste módulo)
 1. PDV-F003+F004+F005 (Fatia 0 — "fundação do pedido"). É indivisível: renomear a tabela,
    resolver preço/custo do catálogo e abrir a leitura são a mesma migration e o mesmo
    refactor. Vem antes de TUDO, inclusive do ciclo de caixa, porque é a única mudança do
    projeto que fica mais cara a cada venda gravada.
 2. PDV-F001+F002+PDV-C004 (Fatia 1 — ciclo de caixa). Espelhe StockCount: o fechamento
    confronta esperado × contado, carimba a divergência e FECHA MESMO ASSIM. Aproveite
    PDV-C002 (trocar o record de domínio por DTO em GET /pdv/sessions) aqui, porque o
    endpoint vai ser reescrito de qualquer jeito.
 3. PDV-F006 (Fatia 3 — pagamento). Depende do ciclo de caixa: o fechamento por forma de
    pagamento é o que dá sentido a order_payment.
 4. PDV-C001 — auditar e completar o README no padrão de estoque. Faça por último, quando
    o módulo tiver o que documentar.

Entre a 2 e a 3 entra a Fatia 2, que é do CRM (cliente identificável no balcão) — veja
docs/dominios/crm/proximos-passos.md. Não é deste módulo, mas bloqueia o cashback.

DECISÕES JÁ TOMADAS — não reabrir
- Venda de balcão e pedido de marketplace são a MESMA entidade (Order com discriminador de
  canal). Não crie uma segunda tabela para o pedido online. §2.1.
- unitPrice SAI do request. O servidor resolve via EstoqueUseCase.findPricingBySku.
  Desconto é discountAmount explícito, sob PDV_SALE_DISCOUNT, com teto em system_config.
  Produto sem preço RECUSA a venda (409 PRODUCT_NOT_PRICED) — preço zero e preço
  desconhecido não são a mesma coisa. §2.3.
- order_item congela unit_price, cost_price E cashback_percent. O snapshot de custo é o
  item mais caro de retrofitar: sem ele, a próxima compra que mudar o costPrice reescreve
  a margem histórica de todos os pedidos passados. §2.3.
- order_number vem de sequência PRÓPRIA e é emitido na CONCLUSÃO, não da BIGSERIAL do id —
  rollback de transação deixa buraco, e buraco em numeração fiscal é problema com o fisco.
- Uma sessão aberta por operador, garantida por índice parcial único no banco, não só pelo
  domínio. §2.7.
- Vender abaixo do custo continua PERMITIDO, só sinalizado — mesma política de
  Pricing.isBelowCost().

QUEBRA DE CONTRATO — já verificada, e é de graça
POST /pdv/sessions/{id}/sales muda de forma incompatível: unitPrice sai do corpo,
warehouseCode sai do corpo (passa a vir da sessão), discountAmount entra.
VERIFICADO EM 2026-07-28: o PDV do frontend-admin (features/pdv/pdv.component.ts) é
INTEIRAMENTE MOCKADO — pdv.mock-data.ts declara "Sem integração com o backend ainda", e o
componente não injeta HttpClient. Não há consumidor real a quebrar. Quebre à vontade.

COMO TRABALHAR
- Um item por vez. Ao terminar cada um, PARE e me mostre o resultado antes de seguir.
- TDD, na ordem domínio → service → persistência → controller → migration.
- NÃO execute ./mvnw. Não há JDK no WSL — eu rodo no PowerShell e reporto. Me entregue o
  comando pronto: ./mvnw test "-Dtest=ClasseA,ClasseB"
- NÃO faça commit. Eu commito manualmente; deixe no working tree.
- Ao concluir: remova o item do "## Backlog do Módulo", acrescente linha em "## Histórico de
  Implementações", registre em docs/feature-registry.md, atualize docs/api-reference.md e a
  collection Postman do módulo, e a data no topo do README.

ARMADILHAS DESTE PROJETO (já custaram build quebrado)
- @DataJpaTest, @WebMvcTest e @AutoConfigureTestDatabase NÃO existem no classpath. Spring
  Boot 4 moveu as slices para módulos por tecnologia. Teste de repositório aqui é
  @SpringBootTest + @ActiveProfiles("dev") + @Transactional, com flush()+clear() explícitos
  antes de reler. Veja EstoqueRepositoryIT.
- Spring Security 7 moveu pacotes: org.springframework.security.access.method saiu, e @P
  virou org.springframework.security.core.parameters.P. Confira o import no jar do ~/.m2 em
  vez de deduzir.
- HexagonalArchitectureTest (ArchUnit) barra import de framework em core/domain e
  core/ports; em core/service só libera org.springframework.transaction.*.
  ApplicationEventPublisher fica no CONTROLLER — é por isso que PdvController.java:90-96
  publica o AuditEvent, e não o service. Siga esse molde.
- A próxima migration é V65. V63 (pricing) e V64 (reserva de estoque) já existem no working
  tree, ainda não commitadas — confira `ls src/main/resources/db/migration | sort -V | tail`
  antes de numerar.
- Seeds de permissão precisam de ON CONFLICT DO NOTHING. O perfil dev NÃO roda Flyway
  (ddl-auto=create-drop), então permissão nova TAMBÉM tem que entrar em SeedConfig e
  DevRoleBootstrapConfig — esquecer isso dá 403 em dev, e já aconteceu duas vezes.
- src/test/resources/application-dev.properties SUBSTITUI o de src/main/resources/,
  nao herda. Propriedade nova que o teste precisa tem que ser escrita nos DOIS.
- A suite NAO executa as migrations: dev tem flyway.enabled=false e monta o schema por
  ddl-auto a partir das entities. O SQL das migrations nunca roda nos testes.
- Todo endpoint novo precisa de @PreAuthorize.
- Testes que escrevem estoque precisam cadastrar o SKU antes: desde EST-C002, movimentar
  SKU inexistente é 404.

Comece lendo o README do módulo e o §2.1/§2.3 do plano, e me apresente o plano da Fatia 0.
```

---

## Por que esta ordem

| Agrupamento | Razão |
|---|---|
| Fatia 0 antes de tudo, inclusive do ciclo de caixa | É a **única** mudança do projeto cujo custo cresce com o tempo. Cada venda gravada sem `cost_price` é uma venda cuja margem real não é mais recuperável — o custo antigo não está em lugar nenhum. O ciclo de caixa, por mais urgente que seja para a operação, custa o mesmo hoje e daqui a três meses. |
| F003, F004 e F005 juntos, não em sequência | São a mesma migration (V65 renomeia `cash_register_sale → sales_order` e acrescenta as colunas) e o mesmo refactor de `PdvService`. Separar significa mexer duas vezes no mesmo arquivo e escrever testes que já nascem obsoletos. |
| PDV-C002 dentro da Fatia 1 | `GET /pdv/sessions` vai ser reescrito de qualquer forma quando a sessão ganhar `expectedAmount`/`countedAmount`/`differenceAmount`. Trocar o record de domínio por DTO agora custa quase nada; depois é uma segunda passada no mesmo endpoint. |
| PDV-C004 dentro da Fatia 1 | Amarrar a sessão ao operador só faz sentido quando existe um jeito de abrir sessão. Antes disso, é regra sobre um fluxo que ninguém consegue exercer. |
| F006 depois do ciclo de caixa | Sem `close()`, o fechamento por forma de pagamento não tem onde ser reportado, e `order_payment` vira tabela que só recebe linha e nunca é lida. |
| PDV-C001 por último | Documentar um módulo que vai ser reescrito três vezes é escrever para jogar fora. |

## Riscos a considerar antes de encarar a lista

**A quebra de contrato deixou de ser risco.** Verificado em 2026-07-28: o PDV do `frontend-admin`
é um protótipo inteiramente mockado (`pdv.mock-data.ts`: *"Sem integração com o backend ainda"*), e
o componente sequer injeta `HttpClient`. Não há consumidor real de `POST /pdv/sessions/{id}/sales`.
O plano dizia que este era "o momento mais barato que vai existir"; na prática é de graça.

**A migração de dado é pequena hoje e só cresce.** `UPDATE sales_order SET channel = 'BALCAO'` e
`order_number = 'LEG-' || LPAD(id::text, 8, '0')` funcionam bem com dezenas de linhas. Os pedidos
legados ficam com `cost_price` e `cashback_percent` nulos para sempre — é deliberado (um default
zero mentiria sobre a margem), mas significa que todo relatório de margem precisa saber conviver
com nulo nos pedidos anteriores à migração.

**O ciclo de caixa é a maior lacuna operacional, e não é o primeiro item.** Isso vai incomodar:
é o que o dono sente na primeira semana. Vale explicitar para ele que a Fatia 0 leva ~5–7 dias e
existe justamente para o ciclo de caixa não precisar ser refeito em seguida.

**~~Ponto em aberto — venda em aberto no balcão.~~ Resolvido em 2026-07-28 a favor do plano.** O
protótipo do `frontend-admin` é um wizard de 3 etapas (produtos → dados do cliente → pagamento) que
termina na liquidação, e a lista "Vendas Registradas Hoje" é histórico de vendas **concluídas**. Não
há comanda, fiado nem retirada posterior. O caminho `BALCAO` pode manter `CRIADO → CONCLUIDO` na
mesma transação, e a reserva de estoque continua sendo pré-requisito só do marketplace.

**Duas lacunas achadas no protótipo que o plano não cobre — decidir antes da Fatia 3:**

1. **Parcelamento de cartão de crédito.** O protótipo tem `installments` no formulário de cartão, e
   o `order_payment` do plano (§2.6) não tem onde guardar isso. Acrescentar `installments INT` na
   tabela é trivial *agora* e chato depois. Vale decidir se o backend registra o parcelamento (útil
   para conciliar repasse da adquirente, que chega parcelado) ou se é dado só da maquininha.
2. **Chave de NF-e por venda.** A lista de vendas do protótipo já exibe `chaveNfe` por venda —
   ou seja, a tela assume emissão fiscal no ato. Isso não muda a decisão de adiar a NFC-e para a
   Fatia 11 (`FIN-F002`), mas significa que a tela vai ficar com um campo vazio até lá. Alinhar a
   expectativa com o dono, senão o PDV "parece incompleto" por um motivo que é de calendário
   fiscal, não de backend.

## O que "módulo operável" significa aqui

Fechar F003, F004, F005, F001, F002, C002, C004 e F006. Com isso o operador abre o caixa pelo
sistema, vende com preço do catálogo e desconto rastreável, registra pagamento em mais de uma
forma, confere a gaveta no fechamento e consegue reler qualquer venda. `PDV-C001` (documentar) e o
cancelamento de venda (Fatia 5, que atravessa estoque e cashback) ficam para depois.
