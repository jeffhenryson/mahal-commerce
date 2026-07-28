# Backlog — Mahal Backend (índice)

> **⚠️ Este arquivo deixou de ser o backlog em 2026-07-26.**
>
> O backlog foi **descentralizado**: cada módulo agora é dono das suas próprias pendências, na
> seção `## Backlog do Módulo` do seu README em `docs/dominios/<modulo>/README.md`, ao lado da
> documentação do que já está implementado. O objetivo foi acabar com a duplicação entre o
> backlog central e os READMEs, que haviam divergido.
>
> Este arquivo permanece apenas como **índice** e como registro do **histórico de sprints**.
> Não adicione itens aqui.

## Onde está cada backlog

| Módulo | README | Séries de ID |
|---|---|---|
| estoque | [`dominios/estoque/README.md`](dominios/estoque/README.md) | `EST-F001+`, `EST-C001+` |
| compras | [`dominios/compras/README.md`](dominios/compras/README.md) | `COM-F001+`, `COM-C001+` |
| vendas-balcao (PDV) | [`dominios/vendas-balcao/README.md`](dominios/vendas-balcao/README.md) | `PDV-F001+`, `PDV-C001+` |
| **pedido** (transversal a canais) | [`dominios/pedido/README.md`](dominios/pedido/README.md) | `PED-F001+`, `PED-C001+` |
| crm | [`dominios/crm/README.md`](dominios/crm/README.md) | `CRM-F001+`, `CRM-C001+` |
| ecommerce | [`dominios/ecommerce/README.md`](dominios/ecommerce/README.md) | `ECM-F001+`, `ECM-C001+` |
| financeiro | [`dominios/financeiro/README.md`](dominios/financeiro/README.md) | `FIN-F001+`, `FIN-C001+` |
| logistica | [`dominios/logistica/README.md`](dominios/logistica/README.md) | `LOG-F001+`, `LOG-C001+` |
| **plataforma** (transversal) | [`dominios/plataforma/README.md`](dominios/plataforma/README.md) | `PLAT-C023+` |

`plataforma` é o módulo guarda-chuva das correções que não pertencem a nenhum domínio de
negócio — segurança, infra, CI/CD, testes, migrations, performance, documentação.

> **Planos transversais.** Itens que atravessam vários módulos continuam registrados no README de
> cada um, mas o desenho e a **ordem de execução** entre eles ficam num documento próprio:
> [`plano-pdv-marketplace.md`](plano-pdv-marketplace.md) (2026-07-28) — PDV operável na loja
> física + marketplace sobre o mesmo estoque, com cashback e kits. Cobre `PDV-F003…F006`,
> `PDV-C004`, `CRM-F001…F003`, `CRM-C005`, `EST-F013…F015`, `EST-F021`, `EST-F022`, `EST-C013`,
> `ECM-F001…F004`, `ECM-C002`, `FIN-F001`, `FIN-F002` e `PLAT-C034` em 12 fatias.
>
> A **ordem de execução dentro de cada módulo** — com as decisões já tomadas, as armadilhas do
> projeto e um prompt pronto para colar numa sessão nova — fica em `proximos-passos.md` ao lado de
> cada README:
> [vendas-balcao](dominios/vendas-balcao/proximos-passos.md) ·
> [crm](dominios/crm/proximos-passos.md) ·
> [estoque](dominios/estoque/proximos-passos.md) ·
> [ecommerce](dominios/ecommerce/proximos-passos.md) ·
> [financeiro](dominios/financeiro/proximos-passos.md) ·
> [plataforma](dominios/plataforma/proximos-passos.md).
> **O módulo da vez é `vendas-balcao`** — as Fatias 0, 1 e 3 são todas dele.

`pedido` é o domínio do **documento de venda**, comum a balcão e marketplace, criado em
2026-07-28 na Fatia 1. O pedido nasce em `vendas-balcao` ou `ecommerce`; `pedido` é onde ele é
modelado e onde o administrador o gerencia (`/orders`).

Domínios ainda sem README: `gestao-empresarial`, `auth`, `notification`, `relatorios`. Serão
criados pelo `/1-analise <dominio>` na primeira análise de cada um.

## Destino das séries de ID legadas

Os IDs foram **preservados sem renumeração**, porque `docs/feature-registry.md` os embute no
nome de cada feature (ex.: `cadastrar-produto (EST-F001)`) e os cards do Notion foram criados
com esses nomes.

| Série legada | Qtde | Onde foi parar |
|---|---|---|
| `C001–C022` — correções transversais, geração 2026-07-20 | 22 | [`plataforma`](dominios/plataforma/README.md), seção Histórico (todas concluídas). Os resíduos explicitamente deixados em aberto viraram `PLAT-C023`–`PLAT-C027`. `C006` também aparece no histórico de [`estoque`](dominios/estoque/README.md); `C018` tem sua parte de estoque rastreada como `EST-C006`. |
| `F001–F009` — features de CRM, geração 2026-07-20 | 9 | [`crm`](dominios/crm/README.md), seção Histórico (todas concluídas) |
| `EST-F001–EST-F016` — roadmap de Estoque, geração 2026-07-15 | 16 | [`estoque`](dominios/estoque/README.md). Concluídas (F001/F002/F003/F004) no Histórico; as 10 abertas no Backlog do Módulo. `EST-F009` e `EST-F010`, que são cruzamentos, também constam no histórico de [`compras`](dominios/compras/README.md) e [`vendas-balcao`](dominios/vendas-balcao/README.md). |

Total migrado: **47 itens**.

## Pipeline

```
/1-analise <dominio>   → grava pendências no README do módulo
/2-sprint              → varre os READMEs e cria os cards no Notion
/3-implementar         → implementa e move o item para o Histórico do README
```

## Histórico de Sprints Criadas

Registro cronológico transversal — permanece aqui porque não pertence a nenhum módulo.
O `/2-sprint` continua acrescentando linhas nesta seção.

> Duas gerações de planejamento. As "Sprints 1–6 (plano de Estoque, 2026-07-15)" foram
> substituídas na prática pela nova numeração de sprints (Correções + CRM), executada a partir
> de 2026-07-20. O roadmap de Estoque restante (EST-F005/F007/F008/F011/F012/F013/F014/F015/F016)
> ainda **não foi re-sprintado**.

**Geração atual (Correções + CRM, a partir de 2026-07-20):**
| Sprint | Data | Itens |
|---|---|---|
| Sprint 1 | 2026-07-20 | C001, C002, C003, F001, F002, F003 |
| Sprint 2 | 2026-07-20 | C004, C005, C006, C007, C008, C009, C010, C011, C012, C013, C014, C015, C016 |

**Geração original (plano de Estoque, 2026-07-15 — substituída/parcialmente executada):**
| Sprint | Data | Itens (nomenclatura original) |
|---|---|---|
| Sprint 1 | 2026-07-15 | EST-F001, EST-F002, EST-F003, C001(estoque) |
| Sprint 2 | 2026-07-15 | EST-F004, EST-F009, EST-F010 |
| Sprint 3 | 2026-07-15 | EST-F006, EST-F007, EST-F008 |
| Sprint 4 | 2026-07-15 | EST-F005, EST-F012, EST-F014 |
| Sprint 5 | 2026-07-15 | infra/paginação/testes/docs de estoque |
| Sprint 6 | 2026-07-15 | EST-F011, EST-F015, EST-F016 |
