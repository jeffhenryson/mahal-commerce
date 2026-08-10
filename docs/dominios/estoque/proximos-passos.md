# Estoque — roteiro para fechar o módulo

**Criado em:** 2026-07-27, logo após a sprint de integridade (EST-C002, C003, C004, C007, C008, C010, C012).
**Para quê:** dar a ordem de execução do backlog restante de estoque, com as dependências reais entre
os itens, até o módulo poder ser considerado fechado e a atenção migrar para outro domínio.

**Revisado em:** 2026-07-28. A ordem mudou — ver o aviso abaixo.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o **roteiro**,
não a lista. Quando um item for concluído, ele sai do backlog do README; este roteiro só precisa ser
atualizado se a ordem mudar.

> **A ordem de 2026-07-27 foi substituída.** O [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md)
> decidiu três itens que estavam em aberto (F013 reserva, F015 kit, F012 transferência) e reordenou o
> resto em função do PDV e do marketplace. Além disso, **a reserva de estoque saiu na frente**: V63
> (pricing) e V64 (reserva) já estão no working tree, com domínio, service, ports e persistência
> escritos. O que valia como "próximo item" (F012) deixou de valer.

---

## Prompt para colar numa sessão nova

```
Continue o desenvolvimento do módulo de ESTOQUE do Mahal backend até fechar o backlog dele.

FONTE DA VERDADE
O backlog vive em docs/dominios/estoque/README.md, seção "## Backlog do Módulo".
docs/backlog.md é só índice e histórico de sprints — não adicione itens lá.
Leia o README inteiro antes de começar: ele documenta o modelo de domínio, as regras
já implementadas, as integrações com compras/vendas-balcao e o histórico.

ESTADO ATUAL
A sprint de 2026-07-27 fechou EST-C002, C003, C004, C005, C007, C008, C009, C010, C011,
C012, F006 e F018. O núcleo transacional está protegido e provado: SKU é validado antes
de qualquer escrita, o @Version tem teste de concorrência, o ledger tem paginação estável,
venda/recebimento deixam trilha de auditoria e o passivo de SKU órfão anterior à
validação já tem ferramenta de levantamento (GET /estoque/integrity/orphan-skus e
scripts/estoque-orphan-skus.sql). A superfície de leitura está validada e paginada:
os 4 endpoints paginados recusam size fora de 1..100 com 400, e GET /estoque/warehouses
passou a devolver PageResult (mudança de contrato).

PENDÊNCIA OPERACIONAL (não é código)
EST-C011 entregou o levantamento, não a limpeza. Falta alguém rodar o diagnóstico contra
a base real e decidir, SKU a SKU, entre cadastrar o produto que faltava e expurgar a
linha. Se eu for solicitado a "terminar o C011", o que cabe é apresentar a lista — não
apagar nada.

TRABALHO CONCLUÍDO EM 2026-07-29 — EST-F013/EST-F021/EST-C013
A reserva estava com o núcleo pronto e sem superfície: StockReservation, ReservationStatus,
StockBalance com reservedQuantity/availableQuantity()/reserve()/consumeReservation()/
releaseReservation() (inclusive apply(SAIDA) já validando contra o DISPONÍVEL), as 7
operações em EstoqueUseCase/EstoqueService (inclusive expireReservations), entity, JpaRepository,
RepositoryImpl, DTOs, converter, exceptions e TTL configurável — tudo sem endpoint, sem
scheduler e sem teste. Fechado agora: GET /estoque/reservations + GET /estoque/reservations/{id}
(ESTOQUE_RESERVATION_READ, só leitura — criar/consumir/liberar é orquestração interna, não
operação de balcão), StockReservationExpiryCleanupService (@Scheduled a cada 5 min +
@SchedulerLock), GET /estoque/integrity/reservation-mismatch (EST-C013, ESTOQUE_STOCK_MANAGE,
molde de EST-C011), handler de StockReservationNotFoundException/NotActiveException, testes de
domínio/controller/security/IT novos, e docs (README, feature-registry, api-reference).
GAP FECHADO EM 2026-07-30: a suíte de teste do núcleo (StockReservation, os 8 métodos de
EstoqueService, StockBalance.reserve/consumeReservation/releaseReservation) que tinha ficado sem
cobertura própria agora tem StockReservationTest, os casos novos de reservedQuantity em
StockBalanceTest e os 8 métodos em EstoqueServiceTest. De brinde, StockReservationExpiryCleanupServiceTest
(único *CleanupService do pacote que ainda não tinha teste). Sem mudança de comportamento ou API.
Path livre para EST-F008/F016 mexerem em StockBalance sem regressão silenciosa.

TRABALHO CONCLUÍDO EM 2026-07-29 — EST-F015/EST-F022 (Fatia 6, kits)
Kit é virtual: ProductType (SIMPLES/KIT) + product_kit_component (um nível só, sem FK — mesmo
motivo de stock_balance/stock_movement). Ciclo/aninhamento impossíveis por construção:
defineKitRecipe recusa componente que não seja SIMPLES, recusa promover a KIT um SKU já usado
como componente de outro kit, recusa kit com variações. getStockBalance deriva o saldo
(min(floor(disponível/receita))); findPricingBySku deriva o custo (soma dos componentes,
null se algum componente não tem custo). A explosão mora inteira em EstoqueService.adjustStock
(chama a si mesmo por componente) — PdvService/OrderService não mudaram. Migration V73. Bug de
passagem corrigido fora do módulo: CashbackService.findMarginImpact excluía todo kit do
relatório por ler product.pricing() cru. Próxima migration livre: V74.

TRABALHO CONCLUÍDO EM 2026-07-30 — EST-F008 (lote e validade)
O núcleo (StockLot, migration V74, FEFO na saída e no consumo de reserva) já estava pronto no
working tree, sem dois pontos que quebravam assim que um SKU virasse lotTracked=true: receber
(ComprasService.receiveGoods) ou estornar (OrderService.refundOrder) esse SKU lançava
MissingLotInfoException e abortava a transação inteira. IMPORTANTE: o desenho que foi escolhido
é ADITIVO, não a reescrita de StockBalance que a seção "Riscos" deste arquivo descrevia (e que
foi corrigida abaixo) — stock_lot é uma quebra por lote ao lado do agregado, mantida na mesma
transação de adjustStock; stock_balance e seu @Version não mudaram, e a reserva continua
lote-agnóstica (reservar contra o disponível agregado basta; só o consumo real precisa do lote).
Fechado agora: lotCode/expiryDate opcionais em GoodsReceiptItem/ComprasService.receiveGoods e em
OrderService.refundOrder (overload com List<RefundItemLot>, casado por SKU); adjustStock recusa
lote informado num SKU que é kit em vez de descartar em silêncio; recordCountedItem ganhou
lotCode e closeStockCount reconcilia cada StockLot via reconciledTo (existia desde a V74, nunca
era chamado) antes de lançar UM AJUSTE agregado; StockLotExpiryAlertService (@Scheduled diário
0 0 7 * * * + @SchedulerLock) liga o findExpiringSoon que também existia sem chamador;
GET /estoque/products/{sku}/lots e GET /estoque/integrity/lot-mismatch (LotIntegrityMismatch,
molde de EST-C011/C013). Migration V75: goods_receipt_item.lot_code/expiry_date;
stock_count_item.lot_code + dois índices únicos parciais substituindo uk_stock_count_item_count_sku
(molde de uk_cashback_rate_active_scope da V69 — @UniqueConstraint não expressa WHERE). Próxima
migration livre: V76. Fora de escopo por decisão: reserva por lote específico não existe, só FEFO
no consumo. Detalhes completos no Histórico de Implementações do README.

ORDEM SUGERIDA (revisada em 2026-07-28 pelo plano de PDV/marketplace; F013/F021/C013 fechados em
07-29; F014/F015/F022 fechados em 07-29; F008 fechado em 07-30; §P1-Estoque do mahal-admin
BACKEND_TODO fechada em 08-10 — upload de imagem, busca/filtro server-side, atributos de raiz,
F020 e categoria, ver README §Histórico de Implementações)
 1. ~~EST-F014~~ ✅ estorno/devolução de venda (Fatia 5 do plano) — `OrderService.refundOrder`
    devolve a mercadoria via `adjustStock(ENTRADA)` por item, com `REVERSED` no cashback e
    estorno de pagamento na mesma transação. Ver `docs/dominios/vendas-balcao/README.md`.
 2. ~~EST-F015 + EST-F022~~ ✅ kit virtual de um nível + custo derivado no Pricing (Fatia 6).
 3. ~~EST-F008~~ ✅ lote e validade — desenho aditivo (StockLot ao lado de StockBalance), sem
              reescrever a granularidade do saldo. Ver bloco acima.
 4. EST-F007  custo médio ponderado. Agora que F008 fechou, o custo já entra por lote — e
              DEPOIS DO CASHBACK (Fatia 4, já fechada): o costPrice manual de V63 já dá a ordem
              de grandeza, que é o que decide se a taxa do carvão é 2% ou 8%.
 5. EST-F016  unidade de medida e conversão — mexe na semântica de quantity em todo lugar.
 6. EST-F005  entrada por XML de NF-e (NfeXmlImportPort). Por último entre as features
              de entrada, porque o XML traz lote e custo — depende de F008 (fechado) e F007.

DESPRIORIZADOS POR DECISÃO (não são esquecimento — §2.2 do plano)
- EST-F012 (transferência entre depósitos) só faz sentido quando existir um SEGUNDO LOCAL
  FÍSICO de verdade. O marketplace NÃO vai usar WarehouseType.ECOMMERCE para separar canal:
  numa loja só a prateleira é uma só, e partir o pool geraria rebalanceamento manual
  permanente. A reserva é o mecanismo que deixa um pool servir dois canais.

FECHADO EM 2026-08-10, CONTRA A RECOMENDAÇÃO ORIGINAL
- EST-F020 (preço por variação) estava aqui como "desaconselhado" (§8.5 do plano: sabores da
  mesma essência custam o mesmo). O dono do produto pediu a implementação mesmo assim. Resolvido
  com herança POR CAMPO do pai como padrão — variação sem preço próprio segue herdando
  exatamente como antes, então o argumento do §8.5 continua valendo como comportamento default,
  só deixou de ser a única opção. Ver `Product.effectivePricingFor` e a entrada de
  `categoria-como-entidade`/`preco-por-variacao` no Histórico de Implementações do README.

NÃO CABEM EM ESTOQUE — me traga a decisão em vez de implementar:
- EST-F011 (curva ABC e giro) está marcado como domínio `relatorios`, que não existe.
  Diga se é para criar o domínio ou trazer para estoque.
- EST-C006 (V45/V47 sem ON CONFLICT) não tem correção possível: migration aplicada não
  se edita sem flyway repair. Confirme que fica como documentação de processo.

COMO TRABALHAR
- Um item por vez. Ao terminar cada um, PARE e me mostre o resultado antes de seguir
  para o próximo.
- TDD, na ordem domínio → service → persistência → controller → migration.
  Teste primeiro, sempre.
- NÃO execute ./mvnw. Não há JDK no WSL — eu rodo os testes no PowerShell e te reporto.
  Me entregue o comando pronto: ./mvnw test "-Dtest=ClasseA,ClasseB"
- Ao concluir: remova o item do "## Backlog do Módulo", acrescente linha em
  "## Histórico de Implementações", registre em docs/feature-registry.md, atualize
  docs/api-reference.md se a API mudou, e a data no topo do README.

ARMADILHAS DESTE PROJETO (já me custaram build quebrado)
- @DataJpaTest, @WebMvcTest e @AutoConfigureTestDatabase NÃO existem no classpath.
  Spring Boot 4 moveu as slices para módulos por tecnologia. Teste de repositório aqui
  é @SpringBootTest + @ActiveProfiles("dev") + @Transactional, com flush()+clear()
  explícitos antes de reler. Veja EstoqueRepositoryIT.
- HexagonalArchitectureTest (ArchUnit) barra qualquer import de framework em core/domain
  e core/ports, e em core/service só libera org.springframework.transaction.*.
  ApplicationEventPublisher e TransactionSynchronizationManager ficam em adapter/infra.
- Migrations: a próxima é V76 (V63-V75 já existem no working tree). Seeds de permissão precisam de ON CONFLICT DO NOTHING.
  O perfil dev NÃO roda Flyway (ddl-auto=create-drop), então permissão nova também tem
  que entrar em SeedConfig e DevRoleBootstrapConfig, senão dá 403 em dev.
- src/test/resources/application-dev.properties SUBSTITUI o de src/main/resources/,
  nao herda. Propriedade nova que o teste precisa tem que ser escrita nos DOIS.
- A suite NAO executa as migrations: dev tem flyway.enabled=false e monta o schema por
  ddl-auto a partir das entities. O SQL das migrations nunca roda nos testes.
- Todo endpoint novo precisa de @PreAuthorize.
- Testes de contexto real que escrevem estoque precisam cadastrar o SKU antes —
  desde EST-C002 movimentar SKU inexistente é 404.

Comece lendo o README do módulo e me apresentando o plano para EST-F007 (custo médio ponderado).
```

---

## Por que esta ordem

Os itens não são independentes, e a ordem abaixo evita refazer trabalho:

| Agrupamento | Razão |
|---|---|
| ~~C011 primeiro~~ ✅ | Feito em 2026-07-27: a ferramenta de levantamento existe. Sobrou só a conferência humana da base real, que não bloqueia os itens de código abaixo. |
| ~~C005 e F018~~ ✅ | Feitos em 2026-07-27, antes das features grandes justamente para não reabrir controller e DTOs depois. |
| ~~F006 **com** C009~~ ✅ | Feitos juntos em 2026-07-27, como previsto: a semântica de `AJUSTE` **era** a modelagem do balanço. `AJUSTE` virou saldo-alvo e o fechamento da contagem é quem o usa em lote. |
| ~~F012 logo depois de F006/C009~~ | **Revogado em 2026-07-28.** O plano decidiu que o marketplace usa **um pool só** e não separa canal por depósito, então a transferência entre depósitos deixa de ter caso de uso até existir um segundo local físico. Ver §2.2. |
| ~~Terminar a reserva antes de abrir item novo~~ ✅ | Fechado 2026-07-29 (EST-F013/F021/C013): endpoint, scheduler de expiração e diagnóstico de integridade. |
| F014 antes de o marketplace usar a reserva | Reserva sem cancelamento é armadilha — estoque travado sem como destravar pela operação. O expirador cobre o caso do abandono, não o do pedido pago que precisa ser desfeito. |
| F008 antes de F007 | O custo entra por lote. Fazer custo médio primeiro e depois introduzir lote significa recalcular a modelagem de custo. |
| F005 por último entre as entradas | O XML de NF-e traz lote e custo prontos. Implementar o import antes de F008 e F007 seria importar campos que ainda não têm onde ser guardados. |

## Riscos a considerar antes de encarar a lista inteira

**EST-F008 (lote e validade) fechou em 2026-07-30 sem o risco que esta seção descrevia.** A
previsão original era mudar a granularidade do saldo de `(sku, depósito)` para
`(sku, depósito, lote)`, reescrevendo `StockBalance`, o `@Version` que protege o saldo, e toda
integração de `compras`/`vendas-balcao`. **Não foi isso que aconteceu:** o desenho que entrou é
**aditivo** — `StockLot` é uma quebra por lote ao lado do agregado, mantida na mesma transação de
`adjustStock`, sem tocar `StockBalance`/`@Version`/reserva. Registrado aqui como lição, não como
crítica: o risco real de uma feature pode ser menor do que a modelagem óbvia sugere, e vale
desenhar antes de assumir que o caminho mais direto é o único.

**EST-F016 (unidade de medida) segue com o perfil de risco que F008 tinha na previsão original**,
sobre `quantity`: passa a existir a distinção entre a unidade de compra e a de venda, e toda
movimentação precisa saber em qual unidade está. Vale reavaliar, à luz do que funcionou em F008,
se dá para um desenho igualmente aditivo antes de assumir que é preciso reescrever `quantity` em
todo lugar.

Se em algum momento for preciso cortar escopo para migrar de módulo mais cedo, **F016 é o
candidato natural** a virar backlog de uma fase posterior. Sem ele o módulo continua coerente: o
que se perde é venda fracionada, não a integridade do saldo.

## O que "módulo fechado" significa aqui

Fechar os grupos que restam no roteiro, mais uma decisão registrada sobre os dois que não cabem
em estoque (F011, C006) e sobre o único item ainda despriorizado (F012 — F020 fechou em
2026-08-10, ver acima). Com isso o backlog do módulo zera e `financeiro` fica destravado para o
DRE, que hoje espera o custo médio de F007.

**O estoque deixou de ser o módulo da vez.** A reserva fechou, `vendas-balcao` fechou suas cinco
fatias (0, 1, 3, 5 e a parte de reembolso da Fatia 5) e o próprio estoque fechou **EST-F014**
(devolução, via `OrderService.refundOrder`), **EST-F015/EST-F022** (kits, Fatia 6) em 2026-07-29 e
**EST-F008** (lote e validade) em 2026-07-30 — ver
[`dominios/vendas-balcao/proximos-passos.md`](../vendas-balcao/proximos-passos.md). Sem fatia
numerada pendente no marco do marketplace. Em 2026-08-10 fechou também a §P1-Estoque do
`BACKEND_TODO.md` do `mahal-admin` — upload de imagem, busca/filtro server-side, atributos de
raiz, F020 (com herança) e categoria como entidade. O que resta do backlog deste módulo (F007,
F016, F005, mais a decisão sobre F011/C006) não bloqueia nada e pode esperar uma janela.
