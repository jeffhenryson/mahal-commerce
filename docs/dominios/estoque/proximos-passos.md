# Estoque — roteiro para fechar o módulo

**Criado em:** 2026-07-27, logo após a sprint de integridade (EST-C002, C003, C004, C007, C008, C010, C012).
**Para quê:** dar a ordem de execução do backlog restante de estoque, com as dependências reais entre
os itens, até o módulo poder ser considerado fechado e a atenção migrar para outro domínio.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o **roteiro**,
não a lista. Quando um item for concluído, ele sai do backlog do README; este roteiro só precisa ser
atualizado se a ordem mudar.

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
A sprint de integridade de 2026-07-27 fechou EST-C002, C003, C004, C005, C007, C008,
C010, C011 e C012. O núcleo transacional está protegido e provado: SKU é validado antes
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

ORDEM SUGERIDA (respeita as dependências reais entre os itens)
 1. EST-F018  PUT/PATCH e desativação de produto e depósito (o campo `active` nunca muda hoje).
 2. EST-F006 + EST-C009  inventário/contagem JUNTO com a semântica de AJUSTE.
              O próprio backlog diz que C009 depende da modelagem de F006 — não separe.
 3. EST-F012  transferência entre depósitos (MovementType.TRANSFER). Depois de 2,
              porque ambos mexem em MovementType e em StockBalance.apply.
 4. EST-F014  estorno/devolução de venda, com rastreabilidade da venda de origem.
 5. EST-F008  lote e validade. Muda a granularidade do saldo — trate como mudança
              de modelagem, não como campo novo.
 6. EST-F007  custo médio ponderado. Vem depois de F008 (o custo entra por lote) e
              destrava o DRE do domínio financeiro.
 7. EST-F016  unidade de medida e conversão — mexe na semântica de quantity em todo lugar.
 8. EST-F015  kit/produto composto.
 9. EST-F005  entrada por XML de NF-e (NfeXmlImportPort). Por último entre as features
              de entrada, porque o XML traz lote e custo — depende de F008 e F007.

NÃO CABEM EM ESTOQUE — me traga a decisão em vez de implementar:
- EST-F013 (reserva de estoque no checkout) depende de carrinho/checkout, e ecommerce
  é esqueleto. Diga o que precisaria existir em ecommerce antes.
- EST-F011 (curva ABC e giro) está marcado como domínio `relatorios`, que não existe.
  Diga se é para criar o domínio ou trazer para estoque.
- EST-C006 (V45/V47 sem ON CONFLICT) não tem correção possível: migration aplicada não
  se edita sem flyway repair. Confirme que fica como documentação de processo.

COMO TRABALHAR
- Um item (ou o par 4) por vez. Ao terminar cada um, PARE e me mostre o resultado
  antes de seguir para o próximo.
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
- Migrations: a próxima é V62. Seeds de permissão precisam de ON CONFLICT DO NOTHING.
  O perfil dev NÃO roda Flyway (ddl-auto=create-drop), então permissão nova também tem
  que entrar em SeedConfig e DevRoleBootstrapConfig, senão dá 403 em dev.
- Todo endpoint novo precisa de @PreAuthorize.
- Testes de contexto real que escrevem estoque precisam cadastrar o SKU antes —
  desde EST-C002 movimentar SKU inexistente é 404.

Comece lendo o README do módulo e me apresentando o plano para o EST-F018.
```

---

## Por que esta ordem

Os itens não são independentes, e a ordem abaixo evita refazer trabalho:

| Agrupamento | Razão |
|---|---|
| ~~C011 primeiro~~ ✅ | Feito em 2026-07-27: a ferramenta de levantamento existe. Sobrou só a conferência humana da base real, que não bloqueia os itens de código abaixo. |
| ~~C005~~ ✅ | Feito em 2026-07-27. |
| F018 antes das features grandes | É barato e mexe na superfície CRUD. Fazer depois significaria reabrir controller e DTOs já modificados pelas features. |
| F006 **com** C009 | O próprio backlog registra que a semântica de `AJUSTE` depende da modelagem de inventário. Separar obriga a decidir duas vezes a mesma coisa. |
| F012 depois de F006/C009 | `MovementType.TRANSFER` e a correção de `AJUSTE` mexem nos mesmos pontos: o enum e `StockBalance.apply`. |
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

Fechar os 9 itens que restam no roteiro, mais uma decisão registrada sobre os três que não cabem
em estoque (F013, F011, C006). Com isso o backlog do módulo zera e `financeiro` fica destravado
para o DRE, que hoje espera o custo médio de F007.
