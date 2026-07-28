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

TRABALHO EM ANDAMENTO — termine antes de abrir item novo
V63 (product pricing) e V64 (stock reservation) estão no working tree, NÃO commitados.
A reserva já tem: StockReservation, ReservationStatus, StockBalance com reservedQuantity /
availableQuantity() / reserve() / consumeReservation() / releaseReservation(), as 7
operações em EstoqueUseCase e EstoqueService (inclusive expireReservations), a entity, o
JpaRepository, o RepositoryImpl, os DTOs, o converter, as 3 exceptions e o TTL configurável
(estoque.reservation.default-ttl, hoje PT30M).
FALTA: endpoints em EstoqueController, o scheduler de expiração chamando expireReservations
(o método existe; o job com @SchedulerLock não — use os *CleanupService existentes como
molde), a suíte de testes inteira (só PricingTest foi escrito) e a documentação/Postman.
Deixar isso pela metade é o pior estado possível: código que ninguém alcança e migration
que ninguém validou.

ORDEM SUGERIDA (revisada em 2026-07-28 pelo plano de PDV/marketplace)
 1. EST-F013 + EST-F021 + EST-C013  terminar a reserva: controller, scheduler de expiração,
              testes, endpoint de integridade (reserved_quantity × soma das reservas ACTIVE)
              e docs. Inclui a mudança de apply(SAIDA) para validar contra o DISPONÍVEL —
              método maduro no caminho crítico de PDV, compras e balanço, então revalide a
              suíte inteira. reserved = 0 preserva o comportamento atual por construção.
 2. EST-F014  estorno/devolução de venda (Fatia 5 do plano). Sobe de prioridade: reserva sem
              cancelamento é armadilha, e a devolução é pré-requisito do cashback estornado.
 3. EST-F015 + EST-F022  kit virtual de um nível + custo derivado no Pricing (Fatia 6).
 4. EST-F008  lote e validade. Muda a granularidade do saldo — trate como mudança
              de modelagem, não como campo novo.
 5. EST-F007  custo médio ponderado. Depois de F008 (o custo entra por lote) e DEPOIS DO
              CASHBACK (Fatia 4): o costPrice manual de V63 já dá a ordem de grandeza, que
              é o que decide se a taxa do carvão é 2% ou 8%.
 6. EST-F016  unidade de medida e conversão — mexe na semântica de quantity em todo lugar.
 7. EST-F005  entrada por XML de NF-e (NfeXmlImportPort). Por último entre as features
              de entrada, porque o XML traz lote e custo — depende de F008 e F007.

DESPRIORIZADOS POR DECISÃO (não são esquecimento — §2.2 e §8.5 do plano)
- EST-F012 (transferência entre depósitos) só faz sentido quando existir um SEGUNDO LOCAL
  FÍSICO de verdade. O marketplace NÃO vai usar WarehouseType.ECOMMERCE para separar canal:
  numa loja só a prateleira é uma só, e partir o pool geraria rebalanceamento manual
  permanente. A reserva é o mecanismo que deixa um pool servir dois canais.
- EST-F020 (preço por variação) desaconselhado: sabores da mesma essência custam o mesmo;
  grade com preços distintos se modela como produtos separados até doer de verdade.

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
- Migrations: a próxima é V65 (V63 e V64 já existem no working tree). Seeds de permissão precisam de ON CONFLICT DO NOTHING.
  O perfil dev NÃO roda Flyway (ddl-auto=create-drop), então permissão nova também tem
  que entrar em SeedConfig e DevRoleBootstrapConfig, senão dá 403 em dev.
- src/test/resources/application-dev.properties SUBSTITUI o de src/main/resources/,
  nao herda. Propriedade nova que o teste precisa tem que ser escrita nos DOIS.
- A suite NAO executa as migrations: dev tem flyway.enabled=false e monta o schema por
  ddl-auto a partir das entities. O SQL das migrations nunca roda nos testes.
- Todo endpoint novo precisa de @PreAuthorize.
- Testes de contexto real que escrevem estoque precisam cadastrar o SKU antes —
  desde EST-C002 movimentar SKU inexistente é 404.

Comece lendo o README do módulo e me apresentando o plano para terminar a reserva (EST-F013/F021/C013).
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
| Terminar a reserva antes de abrir item novo | V64 já está escrita e o core inteiro também. Meia-reserva é pior que reserva nenhuma: a coluna `reserved_quantity` já existe no schema e ninguém a alimenta pela API, então o disponível e o físico coincidem por acidente, não por desenho. |
| F014 antes de o marketplace usar a reserva | Reserva sem cancelamento é armadilha — estoque travado sem como destravar pela operação. O expirador cobre o caso do abandono, não o do pedido pago que precisa ser desfeito. |
| F008 antes de F007 | O custo entra por lote. Fazer custo médio primeiro e depois introduzir lote significa recalcular a modelagem de custo. |
| F005 por último entre as entradas | O XML de NF-e traz lote e custo prontos. Implementar o import antes de F008 e F007 seria importar campos que ainda não têm onde ser guardados. |

## Riscos a considerar antes de encarar a lista inteira

**EST-F008 (lote e validade) é o item mais arriscado.** Ele muda a granularidade do saldo de
`(sku, depósito)` para `(sku, depósito, lote)`. Isso reescreve `StockBalance`, o `@Version` que
protege o saldo, e todas as integrações de `compras` e `vendas-balcao` — que hoje chamam
`adjustStock(sku, warehouseCode, ...)` sem qualquer noção de lote. Não é um campo novo numa tabela;
é uma mudança de modelagem que atravessa três domínios.

**EST-F016 (unidade de medida) tem o mesmo perfil**, sobre `quantity`: passa a existir a distinção
entre a unidade de compra e a de venda, e toda movimentação precisa saber em qual unidade está.

Se em algum momento for preciso cortar escopo para migrar de módulo mais cedo, **F008 e F016 são os
candidatos naturais** a virar backlog de uma fase posterior. Sem eles o módulo continua coerente:
o que se perde é controle de perecível e venda fracionada, não a integridade do saldo.

## O que "módulo fechado" significa aqui

Fechar os 7 grupos que restam no roteiro, mais uma decisão registrada sobre os dois que não cabem
em estoque (F011, C006) e sobre os dois despriorizados (F012, F020). Com isso o backlog do módulo
zera e `financeiro` fica destravado para o DRE, que hoje espera o custo médio de F007.

**O estoque deixou de ser o módulo da vez.** Depois que a reserva fechar (grupo 1), a prioridade do
projeto passa para `vendas-balcao` — as Fatias 0 a 3 do plano são todas de lá, e o estoque só volta
ao centro na Fatia 5 (devolução) e na 6 (kits). Ver
[`dominios/vendas-balcao/proximos-passos.md`](../vendas-balcao/proximos-passos.md).
