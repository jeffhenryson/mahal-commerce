# Estudo — Eventos/RabbitMQ, Spring Modulith e futuro microsserviço

**Data:** 2026-08-11
**Escopo:** brainstorm/estudo arquitetural sobre como evoluir o monolito atual à medida que ele
cresce — não é um plano de execução. Nada deste documento foi implementado; é registro para
retomar a conversa sem reconstruir o raciocínio do zero.
**Base de código analisada:** branch `feat/estoque-catalogo-avancado`, 696 arquivos Java, 15
domínios de negócio.

> **Status:** documento vivo, sujeito a reanálise. Nenhum dos guias abaixo será implementado agora.
> O desenvolvimento do monolito segue normalmente (ex.: features de Estoque em andamento), e a
> validação do que já existe continua sendo feita por uso real da aplicação, conferindo se os
> retornos das operações estão corretos — este estudo não muda a estratégia de testes do que já
> está em desenvolvimento.

## Direção decidida

- Descartada a ideia de "modulitos" internos com RPC + Gateway dentro do próprio monolito.
- Ordem escolhida: **(1) Eventos + RabbitMQ primeiro** (viável hoje, sem reempacotar nada) →
  **(2) estruturar o monolito em módulos com Spring Modulith** (fronteiras por domínio, ainda um
  processo só) → **(3) mais adiante**, um **projeto de microsserviço separado**, nascendo *a
  partir* desse monolito já modularizado — não uma extração incremental modulito-a-modulito no
  mesmo repositório, e sim um repositório novo que reaproveita os módulos e eventos já validados
  como base de desenho.

## Diagnóstico do sistema atual

- Monolito hexagonal (ports & adapters), Spring Boot 4.0.6 / Java 21, 696 arquivos Java, 15
  domínios (`estoque` 27 arquivos, `auth` 16, `crm` 14, os demais 2–6 cada; `financeiro` e
  `logistica` hoje são stubs de ~25 linhas de service).
- Empacotado **por camada, não por domínio**: `adapter.in.controller.EstoqueController`,
  `core.service.EstoqueService`, `core.domain.model.estoque.Product` e
  `core.ports.in.EstoqueUseCase` vivem em subárvores de pacote diferentes.
- `Estoque` funciona como shared kernel: Pedido, PDV, Compras, Pagamento e Cashback chamam
  `EstoqueUseCase` direto e de forma síncrona/transacional. Cashback também lê `CustomerRepository`
  (CRM). Estoque chama `NotificationUseCase`.
- Já existe uma semente de eventing: `core/domain/event/AuditEvent.java` (record + enum
  `EventType`, ~50 tipos), publicado por 17 controllers via `ApplicationEventPublisher`, consumido
  por 4 listeners — o de notificação já roda `@EventListener @Async("taskExecutor")`, com fan-out
  para persistência in-app, SSE e e-mail.
- Infra atual: Postgres, Redis, Prometheus+Grafana, Mailpit. Nenhum broker de mensageria hoje.
  ArchUnit já reforça fronteiras hexagonais. `shedlock-spring` já em uso (sinal de que o time já
  lida com coordenação distribuída).
- Spring Modulith **2.0 GA** (nov/2025) já é compatível com Spring Boot 4/Framework 7 — sem
  bloqueio de versão para adotar sobre o Boot 4.0.6 atual.

---

## Guia 1 — Eventos + RabbitMQ (implementável agora, sem reempacotar nada)

Achado principal do estudo: o subsistema de eventos do Spring Modulith (**Event Publication
Registry** + **externalização para AMQP**) funciona **independente** da estrutura de
módulos/pacotes. Dá pra adotar isso já, no código como ele está hoje.

### O que ele entrega pronto

- **Outbox pattern pronto**: toda publicação via `@ApplicationModuleListener` (atalho para
  `@Async` + `@Transactional(REQUIRES_NEW)` + `@TransactionalEventListener`) é registrada numa
  tabela própria do Postgres, via `spring-modulith-starter-jpa` — reaproveitando o banco já
  existente, sem novo datastore. Se o listener falhar ou o broker cair, a publicação fica marcada
  incompleta e é reprocessada: a garantia de "não perder evento" que teríamos que construir na mão.
- **Externalização automática pro RabbitMQ**: eventos marcados com `@Externalized("rota::#{chave}")`
  são publicados no broker via `spring-modulith-events-amqp` (usa Spring AMQP por baixo; a rota
  vira routing key do RabbitMQ).

### Dependências Maven a adicionar (quando for implementar)

```xml
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-starter-jpa</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-events-api</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.modulith</groupId>
  <artifactId>spring-modulith-events-amqp</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

Mais um BOM `spring-modulith-bom` em `dependencyManagement`, para travar as versões entre si.

### Infra: RabbitMQ nos compose files

Adicionar um serviço `rabbitmq` (imagem `rabbitmq:3-management-alpine`, já com painel de gerência
na porta 15672) em `docker-compose.dev.yml`, `docker-compose.hml.yml` e `docker-compose.prod.yml`,
ao lado do Postgres/Redis já existentes.

### Passo a passo de migração de código

1. **Tipar os primeiros eventos**, saindo do `AuditEvent` genérico para records por domínio.
   Começar pelos tipos de `EventType` já usados pelo `NotificationEventListener` — é o menor
   conjunto e já é fluxo assíncrono hoje. Exemplo adaptado ao domínio real do Mahal:

   ```java
   public record SaldoBaixoEvent(UUID produtoId, UUID warehouseId, int saldoAtual) {}
   ```

2. **Trocar `@EventListener @Async("taskExecutor")` por `@ApplicationModuleListener`** no
   `NotificationEventListener` (ou no novo listener tipado) — ganha o registro no outbox de graça,
   sem mudar a lógica de fan-out (in-app/SSE/e-mail) que já existe.
3. **Marcar os eventos que devem sair pro RabbitMQ** com `@Externalized`, por exemplo:

   ```java
   @Externalized("estoque.saldo-baixo::#{#this.produtoId()}")
   public record SaldoBaixoEvent(UUID produtoId, UUID warehouseId, int saldoAtual) {}
   ```

4. **Rodar os dois modelos em paralelo por um tempo**: manter `AuditEvent` como está para o que já
   funciona (auditoria, métricas de segurança) e ir migrando os fluxos de notificação/cashback/
   estoque um de cada vez para eventos tipados — não precisa ser um "big bang".
5. **Candidato natural para o primeiro experimento real**: fluxo de `Notification`, por já ser
   assíncrono e fan-out hoje, e por ter baixo risco se um evento atrasar ou duplicar.

### Ordem sugerida de expansão (depois do primeiro experimento)

Depois de validar com Notification: Cashback (evento "cashback creditado"), Auditoria (trocar os 4
listeners atuais por consumers tipados), e só depois os eventos ligados a Estoque (ex.:
`SaldoBaixoEvent`, `StockReservedEvent`) — por último porque tocam o domínio mais crítico e mais
mexido no momento pela branch `feat/estoque-catalogo-avancado`.

### Resultado ao final do Guia 1

O monolito continua um processo só, mas os fluxos assíncronos passam a ter outbox garantido e já
publicam pro RabbitMQ — infraestrutura de mensageria validada em produção antes de decidir
qualquer coisa sobre módulos ou microsserviço.

---

## Guia 2 — Estruturação em Spring Modulith (depois do Guia 1)

### Por que isso é um passo separado

A fronteira de módulo do Spring Modulith é **um pacote-raiz + subpacotes**. A estratégia de
detecção padrão ("direct-subpackages") olha os subpacotes diretos do pacote principal — hoje isso
detectaria `adapter`, `core`, `infra` (as camadas), não `estoque`, `crm`, `auth` (os domínios),
porque cada domínio hoje está espalhado em três/quatro subárvores de pacote diferentes por causa
da arquitetura hexagonal atual. **Para o Modulith enxergar os domínios como módulos de verdade, é
preciso reempacotar por domínio primeiro, camada depois.**

### Módulos propostos (mapeando os domínios já levantados)

Um pacote-raiz por domínio, cada um mantendo a divisão hexagonal internamente como subpacote
(`web` = controllers/DTOs, `service`, `domain`, `persistence` = entities/repositories), expondo o
necessário entre módulos via `@NamedInterface`:

```
com.cernecommerce.estoque/       (web, service, domain, persistence)
com.cernecommerce.auth/
com.cernecommerce.crm/
com.cernecommerce.pedido/
com.cernecommerce.cashback/
com.cernecommerce.pdv/
com.cernecommerce.compras/
com.cernecommerce.notification/
com.cernecommerce.rbac/
com.cernecommerce.pagamento/
com.cernecommerce.ecommerce/
com.cernecommerce.storage/
com.cernecommerce.financeiro/
com.cernecommerce.logistica/
com.cernecommerce.config/
```

`infra/` (filtros, handlers, schedulers, audit transversal) tende a virar um módulo de suporte à
parte, sem lógica de negócio, referenciado pelos demais.

### Como cada módulo funcionaria nesse novo modelo

- **Comunicação entre módulos**: prioritariamente via os eventos já tipados/publicados no Guia 1
  (`@ApplicationModuleListener`), não mais chamada direta de service de outro domínio — exceto
  onde consistência forte é indispensável (ex.: reserva de estoque antes de confirmar pagamento),
  que pode continuar como chamada direta a uma interface exposta via `@NamedInterface`,
  propositalmente, enquanto não houver decisão de extrair fisicamente.
- **Verificação automática**: `ApplicationModules.of(Application.class).verify()` roda como teste,
  falhando o build se um módulo passar a depender de pacote interno de outro sem passar por
  interface exposta ou evento — mesmo papel que o ArchUnit já cumpre hoje, só que module-aware.
- **Testes por módulo**: `@ApplicationModuleTest` sobe só o módulo sob teste e seus vizinhos
  diretos (em vez do contexto Spring inteiro) — testes mais rápidos e isolados.
- **Documentação viva**: o `Documenter` do Modulith gera diagramas (C4/PlantUML) direto da
  estrutura real de módulos — dá pra manter os `docs/dominios/*/README.md` que já existem hoje
  atualizados com menos esforço manual.
- **Migração incremental sem travar o build**: módulos legados podem ser declarados `Type.OPEN`
  (relaxa a verificação de dependência) enquanto o acoplamento antigo ainda não foi resolvido, e
  apertados pra `CLOSED` conforme forem migrando pra comunicação por evento.

### Ordem sugerida de repackaging (do menor risco pro maior)

1. **`financeiro` e `logistica`** — hoje são stubs de ~25 linhas, quase nada a mover; bom ensaio do
   processo de repackaging com risco mínimo.
2. **`notification`, `rbac`, `storage`, `config`** — pequenos, baixo acoplamento, consumidores de
   eventos (não fonte de shared kernel).
3. **`crm`, `cashback`, `pedido`, `pdv`, `compras`, `pagamento`** — médios, começam como
   `Type.OPEN` por causa do acoplamento com Estoque, apertando conforme as chamadas diretas virem
   evento.
4. **`estoque` e `auth`** — os maiores e mais centrais; deixar por último, e só depois que a branch
   ativa hoje (`feat/estoque-catalogo-avancado`) estabilizar, para não competir com repackaging
   físico de pacote no mesmo domínio.

Cada domínio migra num PR próprio (rename de pacote via refactor da IDE + ajuste dos imports), não
tudo de uma vez.

---

## Depois disso: microsserviço como projeto à parte (visão de futuro)

Com o monolito já modularizado (Guia 2) e já publicando eventos tipados pro RabbitMQ (Guia 1), a
decisão de nascer um **projeto de microsserviço separado** fica muito mais barata: o novo projeto
consome os mesmos eventos do RabbitMQ que o monolito já externaliza, e o desenho dos seus limites
de domínio já está validado pelas fronteiras de módulo do Modulith — não é mais uma extração
incremental "modulito por modulito" dentro do mesmo repositório, e sim um repositório novo que
nasce em cima de uma base já desacoplada.

## Riscos e trade-offs a manter em mente

- Repackaging físico é mecânico mas toca muitos arquivos — fazer via refactor da IDE (rename de
  pacote), um domínio por vez, evitando sobrepor com feature branches ativas no mesmo domínio.
- Mesmo modularizado, nada obriga a reserva de estoque a virar evento — ela provavelmente continua
  chamada síncrona (bean direto) até uma decisão explícita de extração física; Modulith só
  formaliza a fronteira, não força tudo a ser assíncrono.
- Overhead operacional novo: broker RabbitMQ em todos os ambientes (dev/hml/prod). Mitigado por já
  ter Prometheus/Grafana pra monitorar e Testcontainers pra testar em CI.
- Event Publication Registry cria só uma tabela nova no Postgres já existente — sem custo de infra
  adicional além do próprio broker.

## Próximos passos possíveis (sem compromisso)

- Nenhuma ação de código agora — desenvolvimento do monolito continua normalmente.
- Quando decidirem avançar, o ponto de menor risco para começar é o Guia 1: tipar os eventos de
  notificação e prototipar outbox/RabbitMQ só nesse fluxo, antes de tocar em Estoque ou em
  qualquer reempacotamento de módulo.
