# Domínio: plataforma (correções transversais)

**Status:** 🟢 Ativo — série `C001–C022` concluída; resíduos documentados abaixo
**Escopo:** segurança, infraestrutura, CI/CD, testes, persistência, performance e documentação
**Última atualização deste doc:** 2026-07-26

## Objetivo

Este não é um domínio de negócio. É o módulo guarda-chuva das **correções transversais** do
backend — as que não pertencem a nenhum domínio (estoque, compras, CRM…) porque atravessam
todos: segredos, RBAC, pipeline, migrations, dimensionamento de pool, documentação.

Existe porque o `docs/backlog.md` central foi descentralizado em 2026-07-26 (cada módulo passou
a ser dono do seu próprio backlog) e a série `C001–C022` precisava de um dono.

## Convenção de IDs

- `C001–C022` — série legada, **congelada**. Toda ela está concluída; o registro vive em
  [Histórico de Implementações](#histórico-de-implementações).
- `PLAT-C023+` — novas correções transversais a partir daqui.

Correções que pertencem claramente a um domínio vão para o README daquele domínio, não para cá
(ex.: `EST-C001` em [`estoque`](../estoque/README.md)).

## Testes no Postman

Coleção do módulo: [`plataforma.postman_collection.json`](plataforma.postman_collection.json) — importe no Postman, rode a pasta
`00 — Autenticação` (que faz login e guarda o `accessToken`) e siga as pastas na ordem, ou
rode tudo de uma vez no Collection Runner.

```bash
npx newman run docs/dominios/plataforma/plataforma.postman_collection.json \
  -e docs/postman/mahal-local.postman_environment.json
```

**O que a coleção cobre**

| Pasta | Requisições |
|---|---|
| `01 — Sessões e refresh` | sessões ativas, rotação de refresh token e o 401 de refresh inválido |
| `02 — Usuários` | criação, busca, listagem com filtros e ordenação, atualização, desabilitar/reabilitar (com o 401 do login bloqueado), atribuição e remoção de role, 409 de username duplicado, 400 de senha fora da política, 404 e exclusão |
| `03 — Perfil próprio` | `PATCH /users/me` e troca de senha — **ignoradas por padrão** |
| `04 — Roles` / `05 — Permissões` | listagem, busca, concessão e revogação de permissão em role, 404, e as operações exclusivas de DEV (ignoradas se o usuário não tiver a permissão) |
| `06 — Notificações` | listagem, contador de não lidas, marcar como lida, marcar todas e preferências por tipo |
| `07 — Auditoria` | tipos de evento e listagem filtrada por usuário, ação e intervalo |
| `08 — Sistema` | configuração pública, `/stats` e os endpoints `DEV_ELEVATED` |
| `09 — 2FA` | status (sempre seguro) e o fluxo de setup/confirm/disable — **ignorado por padrão** |
| `10 — Cadastro público` | autoregistro, verificação de e-mail e recuperação de senha — **ignorados por padrão** |
| `11 — Segurança` | 401 sem token, 401 de credencial inválida e o **403** real: login com o usuário `user` e chamada a um endpoint que exige `AUDIT_READ` |

As pastas marcadas como ignoradas são liberadas pelas variáveis `runPerfilProprio`,
`runRevogarSessao`, `run2fa` e `runCadastroPublico` — elas alteram a conta logada ou dependem
de código enviado por e-mail/app autenticador.

`GET /notifications/stream` (SSE) ficou de fora: o Postman não encerra a conexão
`text/event-stream` e trava o Runner. Use `curl -N`.

Convenções, variáveis e o environment compartilhado estão em
[`docs/postman/README.md`](../../postman/README.md).

## Backlog do Módulo

Resíduos explicitamente deixados em aberto pelas notas de implementação de `C001–C022` — nenhum
deles tinha item de backlog próprio até agora.

| ID | Prioridade | Tipo | Item | Descrição | Status |
|---|---|---|---|---|---|
| PLAT-C023 | 🔴 Alta | Correção | rotacionar-segredos-expostos-no-historico | Os 5 segredos de C001 (`JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, `RESEND_API_KEY`, `REDIS_PASSWORD`, `GOOGLE_CLIENT_ID`) foram movidos para `.env`, mas **não rotacionados** — continuam expostos no histórico do git. `RESEND_API_KEY` e `GOOGLE_CLIENT_ID` exigem ação nos consoles de terceiros; rotacionar `TOTP_ENCRYPTION_KEY` exige reencriptar os secrets TOTP já armazenados. Decisão de adiar tomada com o usuário em C001/C002. | Pendente |
| PLAT-C024 | 🔴 Alta | Correção | compose-pai-com-segredos-hardcoded | Existe um terceiro `docker-compose.yml` em `~/Documents/myprojects/mahaltabacaria/` (git separado) que é o que **efetivamente cria os containers do ambiente local** (confirmado via labels `com.docker.compose.project.config_files`). Ele ainda tem os segredos hardcoded originais e não recebeu os fixes de C001/C002/C013/C014 — enquanto não for replicado lá, essas correções não têm efeito prático. Achado registrado durante C013, fora do escopo daquele card. | Pendente |
| PLAT-C025 | 🟡 Importante | Correção | dimensionar-pool-hikaricp | C012 ajustou `server.tomcat.threads.max` para 50 (proporção 5:1), mas o pool HikariCP continua em 10 — a fórmula correta depende do número de vCPUs da instância real de Postgres em produção, dado que não existe no código (`docker-compose.prod.yml` não define requests/limits de CPU). Registrado como ação pendente em `docs/persistence.md`. | Pendente |
| PLAT-C026 | 🟡 Importante | Correção | testes-dedicados-nos-repository-impl | C009 cobriu apenas `RefreshTokenRepositoryImpl.revokeByIdForUser`. As outras ~16 classes `*RepositoryImpl` seguem sem teste dedicado — a Nota C009 registra explicitamente que não havia item de backlog para elas. Sobrepõe-se parcialmente a `EST-C007` (repositórios de estoque). | Pendente |
| PLAT-C027 | 🟢 Melhoria | Correção | validar-pipeline-ci-refatorado | A refatoração de C015 (build único + artifact entre `build-test` e `deploy-ecr`) foi validada só por parse YAML e revisão manual — nunca executada de fato no GitHub Actions. A confirmação (pipeline verde, tempo reduzido) depende do próximo push real a `main`. | Pendente |

## Histórico de Implementações

### Sprint 1 — 2026-07-20

- **C001** 🔴 `remover-segredos-hardcoded-docker-compose` — os 5 segredos movidos para `.env` (gitignored); `docker-compose.yml` referencia `${VAR}`. **Sem rotação** dos valores (ver PLAT-C023).
- **C002** 🔴 `remover-default-totp-encryption-key-prod` — removido o fallback `${TOTP_ENCRYPTION_KEY:-Vx74sQn7...}` de `docker-compose.prod.yml`; `ProdStartupValidator` reforçado para rejeitar explicitamente esse default conhecido. **Sem rotação** do valor (ver PLAT-C023).
- **C003** 🔴 `evict-cache-userdetails-ao-alterar-permissoes` — cache `userDetails` passou a ser evictado quando `RoleService.assignPermission`/`removePermission` altera as permissões de uma role, eliminando a janela de 60s em que uma authority revogada continuava válida.

### Sprint 2 — 2026-07-20 (C004–C016, 13/13)

- **C004** 🟡 `preauthorize-nos-controllers-stub` — os 5 controllers stub (`Compras`, `Ecommerce`, `Financeiro`, `Logistica`, `Pdv`) ganharam `@PreAuthorize` e testes de segurança, saindo do fallback genérico `anyRequest().authenticated()`.
- **C005** 🟡 `validar-seed-dev-password-em-prod` — `seed.dev.password` passou a ser validado no boot, fechando o caminho de criar um `ROLE_DEV` com senha pública do repositório caso `DEV_EMAIL` fosse definido em produção.
- **C006** 🟡 `alinhar-permissoes-seed-dev` — permissões `ESTOQUE_PRODUCT_*` e `ESTOQUE_WAREHOUSE_*` acrescentadas a `SeedConfig.ADMIN_PERMISSIONS`. Detalhes em [`estoque`](../estoque/README.md).
- **C007** 🟡 `teste-integracao-rbac-ponta-a-ponta` — teste que percorre o pipeline real (criar usuário → atribuir role via API → login → JWT com authorities do `CustomUserDetailsService` → endpoint protegido), em vez de injetar authorities via `SecurityMockMvcRequestPostProcessors`.
- **C008** 🟡 `teste-concorrencia-tokens-cas` — testes de race condition para backup code TOTP, token de reset de senha, TOTP challenge token e dev challenge token (todos usam o mesmo padrão CAS via UPDATE condicional).
- **C009** 🟡 `teste-dedicado-refresh-token-repository` — cobertura direta de `RefreshTokenRepositoryImpl.revokeByIdForUser` (proteção contra revogação cruzada de sessão / IDOR). Escopo limitado a essa classe — ver PLAT-C026.
- **C010** 🟡 `indices-username-verification-e-reset` — migration V54 criou índices em `email_verification_codes.username` e `password_reset_tokens.username`, eliminando full table scan nas operações de emissão/limpeza.
- **C011** 🟡 `documentar-batching-e-rollback-de-migrations` — V26 já aplicada em todos os ambientes e imutável para o Flyway; a correção virou documentação de processo em `docs/persistence.md` (padrão de batching por lotes de id + o fato de que o rollback de V26 é impossível sem backup pré-migration, por ser transformação com perda).
- **C012** 🟡 `dimensionar-tomcat-threads` — `server.tomcat.threads.max` explicitado em hml/prod (`${TOMCAT_MAX_THREADS:50}`, era 200 implícito), levando a proporção 5:1 com o pool Hikari. O pool em si não foi alterado — ver PLAT-C025.
- **C013** 🟡 `documentar-topologia-de-deploy` — confirmado que os frontends rodam via compose próprio conectando à network externa `cerne-commerce_default`, e que `docker-compose.prod.yml` já os unifica. Não havia lacuna funcional: resolvido com comentário no topo do compose e a seção "Topologia de deploy" em `docs/architecture.md`.
- **C014** 🟡 `healthchecks-prometheus-grafana` — healthchecks adicionados a `prometheus-mahal` e `grafana-mahal` (via `wget` do busybox, confirmado presente em ambas as imagens). Validado por `docker compose config`, não contra containers reais.
- **C015** 🟡 `build-docker-unico-no-ci` — `build-test` builda uma vez, salva com `docker save | gzip` e sobe como artifact; `deploy-ecr` faz `docker load` + `tag` + `push`, sem rebuild. Removida também a etapa `Build JAR` de `deploy-ecr`, que era desperdício pré-existente (o `Dockerfile` faz seu próprio `mvn package`). Ver PLAT-C027.
- **C016** 🟡 `validar-avatar-base-url-em-hml` — `avatar.base-url` acrescentado ao `HmlStartupValidator` (rejeita ausente/`localhost`/`example.com`) e `ProdStartupValidator` reforçado para também rejeitar `example.com`.

### Correções posteriores (C017–C022)

- **C017** 🟢 `rebranding-cerne-para-mahal` — renomeação `cerne-commerce` → `mahal-commerce` em 14 arquivos (pom, Dockerfile, properties, `JwtService`, README, CI, composes, prometheus, alertas e dashboard Grafana). **Não alterados** por risco: o groupId/pacote Java `com.cernecommerce` (tocaria centenas de arquivos), os volumes Docker `external: true` (renomear apontaria para volumes inexistentes) e a network/serviço de frontend em `docker-compose.prod.yml`. Build Maven e `docker build` validados de ponta a ponta; suíte com 910 testes, 0 falhas, 1 erro (flake pré-existente).
- **C018** 🟢 `documentar-migrations-seed-sem-on-conflict` — mesma situação de C011: V19/V45/V47 já aplicadas, editar quebraria o checksum do Flyway. Documentado em `docs/persistence.md` com checklist para migrations de seed futuras. A parte específica de estoque (V45/V47) segue rastreada como `EST-C006`.
- **C019** 🟢 `pool-de-threads-do-scheduler` — `spring.task.scheduling.pool.size` configurado; os 8 jobs `@Scheduled` concentrados entre 03:00–04:00 não competem mais por um único thread.
- **C020** 🟢 `renomear-servico-app-no-compose` — serviço `app` alinhado ao padrão `-mahal` dos demais; comentário de porta de scrape do Prometheus corrigido.
- **C021** 🟢 `corrigir-docs-desatualizadas` — `docs/testing.md` (contagem real de arquivos de teste) e `docs/security.md` (H2 console é `permitAll()` total, mitigado por existir só em `@Profile("dev")`).
- **C022** 🟡 `aceitar-backup-code-no-change-password` — `ChangePasswordRequest.totpCode` tinha `@Size(min=6, max=8)` enquanto os backup codes reais têm formato `XXXX-XXXX-XXXX` (14 caracteres): `PUT /users/me/password` rejeitava **todo** backup code com 400 antes de chegar na validação. Corrigido.
