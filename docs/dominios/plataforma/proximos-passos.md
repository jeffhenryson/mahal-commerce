# Plataforma — roteiro das correções transversais

**Criado em:** 2026-07-28, a partir do [`plano-pdv-marketplace.md`](../../plano-pdv-marketplace.md).
**Para quê:** dar ordem ao backlog transversal — segurança, infra, CI/CD, testes — e deixar claro
**quais itens deixam de ser opcionais** quando o marketplace abrir uma superfície pública.

O backlog em si continua em [`README.md`](README.md#backlog-do-módulo) — este arquivo é o
**roteiro**, não a lista.

---

## Os dois itens que viram bloqueantes

Hoje ambos são "importantes". Na **Fatia 8** do plano (autenticação de cliente + catálogo público)
eles passam a ser pré-requisito, e a razão é a mesma nos dois casos: **até agora, todo usuário
autenticado era um funcionário.**

| Item | Por que vira bloqueante |
|---|---|
| **PLAT-C034** — ArchUnit exigindo `@PreAuthorize` | `SecurityConfig.java:117` termina em `anyRequest().authenticated()`. Qualquer método de controller sem `@PreAuthorize` explícito é alcançável por qualquer autenticado. Hoje isso é inócuo porque todos têm a anotação — mas quando `ROLE_CUSTOMER` existir, "autenticado" passa a incluir cliente final, e um endpoint de operador que esqueça a anotação vira exposição de dado interno. Transformar a convenção em garantia é barato e fecha uma classe inteira de regressão futura. |
| **PLAT-C030** — rate limit em endpoints de negócio | `LoginRateLimitingFilter` cobre só `/auth/**`. `/shop/catalog` é **público** e `/shop/register` **cria linhas** sem autenticação — é convite a enumeração de catálogo e a flood de cadastro. Some-se a isso o `GET /crm/customers/export` já existente, que devolve a base inteira sem paginação. |

Fazer os dois **antes** da Fatia 8, não durante.

---

## Prompt para colar numa sessão nova

```
Continue o backlog transversal do módulo PLATAFORMA do Mahal backend.

FONTE DA VERDADE
Backlog: docs/dominios/plataforma/README.md, seção "## Backlog do Módulo".
Roteiro: docs/dominios/plataforma/proximos-passos.md.
plataforma é o guarda-chuva das correções que não pertencem a nenhum domínio de negócio —
segurança, infra, CI/CD, testes, migrations, performance, documentação.

ESTADO ATUAL
As sprints de 2026-07 fecharam PLAT-C028, C029 e C032. Continuam abertos C023, C024, C025,
C026, C027, C030, C031, C033 e C034.

ORDEM SUGERIDA
 1. PLAT-C034 — teste ArchUnit exigindo @PreAuthorize em todo método de controller fora de
    /auth e /shop, no molde do HexagonalArchitectureTest existente. Barato, e precisa estar
    pronto ANTES da Fatia 8 do plano.
 2. PLAT-C030 — rate limit em endpoints de negócio. Também pré-requisito da Fatia 8.
 3. PLAT-C023 + PLAT-C024 — segredos: rotação dos 5 expostos no histórico do git, e o
    compose pai em ~/Documents/myprojects/mahaltabacaria/ que é quem EFETIVAMENTE cria os
    containers locais e nunca recebeu os fixes. Enquanto C024 não for replicado lá, várias
    correções anteriores não têm efeito prático.
 4. PLAT-C031 — Grafana anônimo com embedding no compose de hml.
 5. PLAT-C026 — testes dedicados nos ~16 *RepositoryImpl sem cobertura.
 6. PLAT-C033 — decidir entre remover ou voltar a usar as 4 permissões órfãs de role/
    permission CRUD.
 7. PLAT-C025 — dimensionar o pool HikariCP. Depende de um dado que não está no código
    (vCPUs da instância real de Postgres em produção) — me traga a pergunta, não um chute.
 8. PLAT-C027 — validar o pipeline CI refatorado. Depende do próximo push real a main; não
    é implementação.

ITENS QUE NÃO SÃO CÓDIGO — me traga a decisão em vez de executar
- PLAT-C023 exige ação nos consoles de terceiros (Resend, Google) e reencriptar os secrets
  TOTP já armazenados. Não rotacione nada sem confirmação explícita minha.
- PLAT-C024 mexe num repositório git SEPARADO, fora deste projeto.
- PLAT-C027 só se confirma com pipeline verde no GitHub Actions.

COMO TRABALHAR
- Um item por vez. Ao terminar cada um, PARE e me mostre o resultado.
- NÃO execute ./mvnw (sem JDK no WSL). Me entregue o comando pronto:
  ./mvnw test "-Dtest=ClasseA,ClasseB"
- NÃO faça commit. Eu commito manualmente.
- Ao concluir: mova o item para "✅ Concluído" no backlog, acrescente no Histórico de
  Implementações e registre em docs/feature-registry.md.

ARMADILHAS
- @DataJpaTest e @WebMvcTest NÃO existem no classpath (Spring Boot 4 moveu as slices).
- Spring Security 7 moveu pacotes: access.method saiu, @P virou core.parameters.P. Confira
  o import no jar do ~/.m2 em vez de deduzir.
- HexagonalArchitectureTest é o molde para qualquer regra ArchUnit nova.
- A próxima migration é V65 — V63 e V64 já existem no working tree, não commitadas.

Comece pelo PLAT-C034 e me apresente o plano.
```

---

## Por que esta ordem

| Agrupamento | Razão |
|---|---|
| C034 e C030 primeiro | São os únicos com **prazo**: a Fatia 8 do plano não deve começar sem eles. Os demais são dívida que não piora com o tempo. |
| C023 e C024 juntos | Rotacionar segredo sem corrigir o compose que efetivamente sobe os containers é rotacionar para nada. C024 é pré-condição prática de C023. |
| C025 e C027 por último | Nenhum dos dois se resolve escrevendo código: um depende de um dado de produção que não existe no repositório, o outro de um push real a `main`. |

## Riscos a considerar

**PLAT-C024 é o item mais fácil de subestimar.** Existe um terceiro `docker-compose.yml` em
`~/Documents/myprojects/mahaltabacaria/` (git separado) que é o que realmente cria os containers do
ambiente local — confirmado pelas labels `com.docker.compose.project.config_files`. Correção de
segredo aplicada só neste repositório **não tem efeito prático** enquanto não for replicada lá. Vale
para C001, C002, C013 e C014, já fechados.

**PLAT-C023 não é uma tarefa de código.** Rotacionar `TOTP_ENCRYPTION_KEY` significa reencriptar os
secrets TOTP já armazenados, e `RESEND_API_KEY`/`GOOGLE_CLIENT_ID` exigem ação em consoles de
terceiros. A decisão de adiar foi tomada com o usuário em C001/C002 — reabrir exige nova conversa,
não iniciativa.

## O que "backlog transversal em dia" significa aqui

C034 e C030 fechados antes da Fatia 8; C023 e C024 resolvidos como par; e os demais absorvidos nas
janelas entre fatias de negócio. Este backlog nunca zera de verdade — é onde vai parar tudo que não
pertence a um domínio — mas os itens com prazo são apenas os dois primeiros.
