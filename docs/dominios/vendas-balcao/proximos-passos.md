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

## Módulo fechado — o que falta é só documentação e o que depende de outros módulos

As Fatias 0, 1 e 3 (fundação do pedido, ciclo de caixa + `/orders` admin + liquidação cruzada,
pagamento com troco e comprovante) são todas deste módulo e estão **todas fechadas** — ver
[Histórico do README](README.md#histórico-de-implementações). O que resta:

- **`PDV-C001`** — auditar o código e completar este README (Modelo de Domínio, Regras de
  Negócio, Schema, Cobertura de Testes). Único item que ainda é só deste módulo.
- ~~**`PDV-F007`** (estorno de pagamento no cancelamento) — bloqueado até `CRM-F003` (cashback,
  Fatia 4) existir~~ — **fechado em 2026-07-29** via Fatia 5 (`REEMBOLSADO`/`refundOrder`), ver
  [Histórico do README](README.md#histórico-de-implementações).

Decisões resolvidas no caminho, registradas no Histórico do README para não precisar reabrir:
parcelamento de crédito (`installments` em `order_payment`), troco só atribuível a dinheiro em
pagamento dividido (regra mais estrita que o §2.6 original do plano), e o `expectedAmount` do
fechamento passou a somar só `DINHEIRO` capturado, não mais o líquido de toda venda concluída.

**Armadilhas ainda válidas para quem mexer aqui de novo:** `@DataJpaTest`/`@WebMvcTest` não
existem no classpath (Spring Boot 4); IT de repositório é `@SpringBootTest` +
`@ActiveProfiles("dev")` + `@Transactional` com `flush()`+`clear()`; `HexagonalArchitectureTest`
barra framework em `core/domain`/`core/ports`, e `ApplicationEventPublisher` só pode aparecer no
controller; a suíte não roda migrations (schema vem de `ddl-auto` a partir das entities); toda
permissão nova entra em `SeedConfig` **e** `DevRoleBootstrapConfig`. Próxima migration livre: V73.
