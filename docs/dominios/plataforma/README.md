# Domínio: plataforma (correções transversais)

**Status:** 🟢 Ativo — série `C001–C022` concluída; resíduos documentados abaixo
**Escopo:** segurança, infraestrutura, CI/CD, testes, persistência, performance e documentação
**Última atualização deste doc:** 2026-07-27

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

## Segurança e Infraestrutura

> Esta é a seção **dona** do assunto no backend. O detalhamento mecânico de cada peça está em
> [`docs/security.md`](../../security.md) (filtros, JWT, refresh, TOTP, CORS, headers, métricas)
> e [`docs/infrastructure.md`](../../infrastructure.md) (ambientes, containers, datastores,
> CI/CD, IaC). Os READMEs dos domínios de negócio trazem só o recorte deles e apontam para cá.

### Modelo de autorização

RBAC **dinâmico**: roles e permissões são linhas nas tabelas `roles`, `permissions` e
`role_permissions` — **não existe `enum Role` em Java**. `core/domain/model/rbac/Role` e
`Permission` são records de domínio sem valores fixos.

Convenção obrigatória, documentada em `infra/config/security/SecurityConfig.java:53-57`: usar
sempre `hasAuthority()`, **nunca** `hasRole()`. Roles carregam o prefixo `ROLE_`; permissões não.

| Role | Criada por | Profiles | Recebe |
|---|---|---|---|
| `ROLE_ADMIN` | `infra/config/SeedConfig.java` | **só `dev`** | os 25 nomes de `ADMIN_PERMISSIONS` |
| `ROLE_USER` | `infra/config/SeedConfig.java` | **só `dev`** | apenas `USER_READ` |
| `ROLE_DEV` | `infra/config/DevRoleBootstrapConfig.java` | `dev`, `hml`, `prod` | `ADMIN_PERMISSIONS` + `DEV_ROLE_MANAGE` + `DEV_PERMISSION_MANAGE` |

Em `hml`/`prod` as permissões vêm das migrations de seed (Flyway); em `dev` não há Flyway
(`ddl-auto=create-drop`), então `SeedConfig`/`DevRoleBootstrapConfig` são a **única** fonte —
uma permissão criada só por migration não existe em dev. Foi a causa raiz de `EST-C001`.

`POST /auth/register` não atribui role alguma por default (`auth.registration.default-roles`
vazio): o usuário autorregistrado nasce autenticável e sem nenhuma authority.

### Permissões — catálogo completo

30 permissões persistidas, mais duas authorities que não são linha de tabela.

| Grupo | Permissões | Migration |
|---|---|---|
| Usuários | `USER_CREATE`, `USER_READ`, `USER_UPDATE`, `USER_DELETE`, `USER_ROLE_ASSIGN`, `USER_STATUS` | V4, V7, V8 |
| Roles e permissões | `ROLE_READ`, `ROLE_MANAGE_PERMISSIONS`, `PERMISSION_READ` | V9 |
| Auditoria | `AUDIT_READ` | V19 |
| DEV | `DEV_ROLE_MANAGE`, `DEV_PERMISSION_MANAGE` | V36 |
| Estoque | `ESTOQUE_PRODUCT_READ/MANAGE`, `ESTOQUE_WAREHOUSE_READ/MANAGE`, `ESTOQUE_STOCK_MANAGE` | V45, V47, V56 |
| CRM | `CRM_CUSTOMER_READ`, `CRM_CUSTOMER_MANAGE` | V48 |
| Compras | `COMPRAS_READ`, `COMPRAS_RECEIPT_MANAGE` | V53, V60 |
| PDV | `PDV_READ`, `PDV_SALE_MANAGE` | V53, V57 |
| Stubs | `ECOMMERCE_READ`, `FINANCEIRO_READ`, `LOGISTICA_READ` | V53 |
| **Órfãs** | `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE`, `PERMISSION_DELETE` | V9, V36 — ver PLAT-C033 |

Authorities que **não** existem na tabela `permissions`:

- **`DEV_ELEVATED`** — injetada no JWT por `core/service/AuthService.java:137` após o duplo TOTP
  de elevação (`POST /auth/dev/first-code` → `POST /auth/dev/complete`). Protege
  `/system/config`, `/system/info` e, em `dev`, `/actuator/**`. Vive só na sessão elevada.
- **`ROLE_DEV`** — é uma role, mas aparece em `@PreAuthorize("hasAuthority('ROLE_DEV')")` em
  `DevAuthController.java:53`, o que é válido porque roles também são authorities.

### Endpoints do módulo × permissão

| Método | Rota | Permissão | Controller |
|---|---|---|---|
| `POST` | `/users` | `USER_CREATE` | `UserController:61` |
| `GET` | `/users`, `/users/{username}` | `USER_READ` | `UserController` |
| `PUT` | `/users/{id}` | `USER_UPDATE` | `UserController` |
| `DELETE` | `/users/{id}` | `USER_DELETE` | `UserController` |
| `POST`/`DELETE` | `/users/{username}/roles/{roleName}` | `USER_ROLE_ASSIGN` | `UserController:78` |
| `PUT` | `/users/{id}/disable`, `/users/{id}/enable` | `USER_STATUS` | `UserController:216` |
| `GET` | `/roles`, `/roles/{name}` | `ROLE_READ` | `RoleController:61,89` |
| `POST`/`DELETE` | `/roles`, `/roles/{name}` | `DEV_ROLE_MANAGE` | `RoleController:102,120` |
| `POST`/`DELETE` | `/roles/{r}/permissions/{p}` | `ROLE_MANAGE_PERMISSIONS` | `RoleController:136,161` |
| `GET` | `/permissions` | `PERMISSION_READ` | `PermissionController` |
| `POST`/`DELETE` | `/permissions` | `DEV_PERMISSION_MANAGE` | `PermissionController:91` |
| `GET` | `/audit-logs`, `/audit-logs/actions` | `AUDIT_READ` | `AuditLogController:41` |
| `GET`/`PUT` | `/system/config`, `/system/config/{key}` | `DEV_ELEVATED` | `SystemConfigController:34,41` |
| `GET` | `/system/info` | `DEV_ELEVATED` | `SystemInfoController:28` |
| `POST` | `/auth/dev/first-code` | `ROLE_DEV` | `DevAuthController:53` |
| `GET` | `/stats` | `USER_READ` **e** `ROLE_READ` | `StatsController:27` |

**Endpoints protegidos apenas por `anyRequest().authenticated()`** — sem `@PreAuthorize`, por
decisão consciente (o recurso é sempre do próprio usuário autenticado):
`NotificationController`, `NotificationPreferenceController`, `TotpController`,
`AvatarController` (upload/remoção do próprio avatar) e `/users/me/**` no `UserController`.
`AuthController`, `OAuthController` e `RegistrationController` são públicos por natureza.

### Rotas públicas

Definidas em `SecurityConfig.java:74-114`. A **ordem importa**: as regras de `/auth/sessions`,
`/auth/2fa/*` e `/auth/dev/first-code` precisam vir **antes** do `permitAll` genérico de
`/auth/**`, que é mais amplo e as engoliria (há comentário fixando isso no código).

| Rota | Regra |
|---|---|
| `/auth/**` | `permitAll` (catch-all, por último) |
| `/auth/sessions` (GET/DELETE), `/auth/sessions/*` (DELETE) | `authenticated` |
| `/auth/2fa/setup`, `/auth/2fa/confirm`, `/auth/2fa/replace`, `DELETE /auth/2fa`, `GET /auth/2fa/status`, `/auth/2fa/backup-codes/regenerate` | `authenticated` |
| `/auth/2fa/verify`, `/auth/dev/complete` | `permitAll` (o token de desafio é a prova de identidade) |
| `/system/config/public` | `permitAll` |
| `GET /avatars/*` | `permitAll` |
| `/actuator/health/**`, `/actuator/info` | `permitAll` |
| `/actuator/**` | `permitAll` em `hml`/`prod` (porta 8081 isolada por rede); `DEV_ELEVATED` em `dev` |
| `/v3/api-docs/**`, `/swagger-ui/**` | `permitAll` **em todos os ambientes** quando `springdoc.swagger-ui.enabled=true` — ver PLAT-C029 |

### Cadeia de filtros

Ordem em `SecurityConfig.java:118-121`, todos antes do `UsernamePasswordAuthenticationFilter`:

1. `TraceIdFilter` — `X-Trace-Id` no MDC (só aceita o header entrante se casar com o regex
   `SAFE_TRACE_ID`) e popula o `DeviceInfoContext` (IP + User-Agent truncado em 512).
2. `MaintenanceModeFilter` — 503 quando `security.maintenance.enabled`.
3. `LoginRateLimitingFilter` — ver abaixo.
4. `JwtAuthenticationFilter` — só header `Authorization: Bearer`; consulta a blocklist por `iat`
   e revalida `user.isEnabled()` como defesa em profundidade.

Sessão `STATELESS`, `httpBasic` desabilitado, CSRF desabilitado (API com Bearer token; o cookie
de refresh é mitigado por `SameSite=Strict` + `Path=/auth`). 401/403 saem como JSON via
`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`.

### Rate limiting

Sliding window **por IP**, aplicada só a um conjunto fechado de rotas
(`LoginRateLimitingFilter.shouldNotFilter`, linhas 42-77). Resposta: **429** com `Retry-After`,
corpo `ApiError`, e incremento da métrica `auth.rate_limit.blocked.total`.

| Profile | Janela | Máx. |
|---|---|---|
| `dev` | 60s | 10 |
| `hml` | 60s | 10 |
| `prod` | 60s | **5** |

Rotas cobertas: `POST` em `/auth/login`, `/auth/register`, `/auth/verify-email`,
`/auth/resend-verification`, `/auth/refresh`, `/auth/forgot-password`, `/auth/reset-password`,
`/auth/2fa/verify|confirm|replace`, `/auth/2fa/backup-codes/regenerate`, `/auth/oauth2/google`,
`/auth/dev/first-code`, `/auth/dev/complete`; `DELETE /auth/2fa`;
`PUT /notifications/preferences/**`; `GET /notifications/stream`.

❌ **Nenhum endpoint de negócio é limitado** — `/estoque/**`, `/crm/**`, `/compras/**`, `/pdv/**`
não passam pelo filtro. Ver PLAT-C030.

Implementação: `RedisLoginRateLimiterAdapter` em `hml`/`prod` (script Lua atômico com
`ZADD`/`ZREMRANGEBYSCORE`/`ZCARD`, chave `rate:login:{ip}`, member com UUID para não colidir em
timestamps iguais) e `InMemoryLoginRateLimiterAdapter` em `dev`. O IP vem de
`request.getRemoteAddr()`, confiando em `server.forward-headers-strategy=native` atrás do Traefik.

Complementar e independente: **lockout por usuário** (`auth.lockout.max-attempts=5`,
`auth.lockout.duration-minutes=15`), que bloqueia a conta e não o IP.

### Isolamento de dados

O sistema é **single-tenant**. Não existe `tenant_id`, `company_id`, `store_id` nem RLS em
nenhuma das tabelas — nem `@Filter`/`@TenantId` do Hibernate. `warehouse` (V46) é depósito
logístico, não unidade organizacional.

O único isolamento é **por usuário**, e só em auth: `AuthService.revokeSession` delega para
`RefreshTokenRepositoryImpl.revokeByIdForUser`, que casa o id da sessão com o username — a
proteção contra IDOR entre sessões (coberta por teste em C009).

Consequência prática: **qualquer usuário com a permissão de um domínio vê todos os dados daquele
domínio.** Não há como restringir um vendedor a um depósito ou uma carteira de clientes.

### Auditoria

`AuditEvent` (`core/domain/event/AuditEvent.java`) tem ~50 `EventType` cobrindo auth, ciclo de
vida de usuário, reset de senha, RBAC, 2FA, elevação DEV, OAuth, estoque e CRM. Pipeline:

`publisher.publishEvent(AuditEvent.of(...))` → `infra/audit/AuditEventListener` (loga e resolve
o IP **na thread do request**, antes do salto async; respeita `module.audit-logs.enabled`) →
`AuditPersistenceService` (grava `audit_logs` de forma assíncrona). Os mesmos eventos alimentam
`SecurityMetricsEventListener` e `NotificationEventListener`.
`AuthenticationEventsListener` captura ainda os eventos nativos do Spring Security — o
`AuthenticationEventPublisher` é fiado manualmente em `SecurityConfig.java:141` porque o
`ProviderManager` criado por código usa `NullEventPublisher` por padrão.

Tabela `audit_logs` (V18): `username`, `action`, `target`, `details`, `ip_address`, `timestamp`,
com índices em `username`, `action`, `timestamp DESC` e o composto `username + timestamp DESC`
(V20). Retenção `audit.retention-days=365`, limpeza em `AuditLogCleanupService`
(cron `0 45 3 * * *`). Leitura por `GET /audit-logs` (`AUDIT_READ`).

**Cobertura desigual:** os domínios `compras` e `vendas-balcao` não publicam nenhum
`AuditEvent`, e o export CSV de clientes também não — ver `COM-C003`, `PDV-C003`, `EST-C004` e
`CRM-C002` nos READMEs dos respectivos domínios.

### Conformidade (LGPD)

❌ **Não há nada implementado.** Nenhuma menção a consentimento, anonimização, exportação de
dados pessoais ou direito ao esquecimento no código. A exclusão de usuário é **soft delete**
(`deleted_at` + `@SQLRestriction`, V24), o que preserva o dado em vez de removê-lo. Se LGPD
entrar em escopo, isso vira trabalho de domínio próprio, não ajuste pontual.

### Validação e uploads

- Bean Validation em 18 controllers (`@Valid`), com tratamento central em
  `infra/handler/GlobalExceptionHandler` no formato `ApiError` + `traceId`.
- Upload: `spring.servlet.multipart.max-file-size=3MB`; limite de negócio de avatar 2 MB
  (`AvatarProperties`). O formato é validado por **magic bytes** (JPEG/PNG/WebP) em
  `AvatarService`, não por `Content-Type`, e o arquivo é salvo como `UUID + extensão detectada`
   — o nome enviado pelo cliente nunca é usado.
- Senhas: BCrypt. `eraseCredentialsAfterAuthentication=false` é intencional e comentado em
  `SecurityConfig.java:126-130` (o erase zeraria o hash dentro do objeto em cache).

### Infraestrutura transversal

| Recurso | Papel | Onde |
|---|---|---|
| Postgres 16 + Flyway (V1–V61) | dado de todos os domínios; H2 em `dev` | [`docs/infrastructure.md`](../../infrastructure.md#datastores) |
| Redis 7 | rate limit, lockout, blocklist de token e cache (`hml`/`prod`) | adapters em `adapter/out/redis/` |
| Cache Spring | `userDetails`, `users`, `userAuthorities`, TTL 60s | evict ao alterar permissões de role (C003) |
| S3 + CloudFront | avatares em `prod` | `S3AvatarStorageAdapter` |
| SSE | notificações em tempo real | `SseEmitterRegistry` |
| ShedLock | impede os 8 jobs `@Scheduled` de duplicarem entre instâncias | tabela `shedlock` (V11) |
| Prometheus + Grafana | métricas, dashboards e alertas | `prometheus.yml`, `grafana/provisioning/` |
| Validadores de boot | recusam subir `hml`/`prod` com segredo ausente ou placeholder | `infra/config/startup/` |

### Riscos conhecidos

Todos rastreados no [Backlog do Módulo](#backlog-do-módulo): PLAT-C023 (segredos não
rotacionados), PLAT-C024 (compose pai fora do repo), PLAT-C028 (chave TOTP versionada),
PLAT-C029 (spec pública em prod), PLAT-C030 (sem rate limit no negócio), PLAT-C031 (Grafana
anônimo), PLAT-C032 (profile default `dev`), PLAT-C033 (permissões órfãs).

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
| PLAT-C028 | 🔴 Alta | Correção | fallback-totp-key-reintroduzido-no-compose-prod | `docker-compose.prod.yml:60` voltou a ter fallback hardcoded: `${TOTP_ENCRYPTION_KEY:-zNEtKjyPPpEkAIBZBZ29nixcQCcAcA19ExgMgaQVRjg=}` (commit `ca2ee50`). É regressão de **C002**, que removeu exatamente esse padrão. Pior: `ProdStartupValidator.java:22` só conhece a chave antiga (`Vx74sQn7…`), então o boot **não** bloqueia esta — produção sobe silenciosamente com uma chave AES versionada no repositório, e é ela que cifra os secrets TOTP. Distinto de PLAT-C023, que trata da rotação dos valores já expostos. | Pendente |
| PLAT-C029 | 🟡 Importante | Correção | swagger-spec-publico-em-prod-contradiz-doc | `SecurityConfig.java:75-80` dá `permitAll()` a `/v3/api-docs/**` e `/swagger-ui/**` em todos os ambientes, com comentário afirmando que é intencional. `application-prod.properties:54` afirma o contrário: "spec (`/v3/api-docs/**`) exige ROLE_DEV". Uma das duas está errada e hoje o spec completo da API é público em produção. Decidir e alinhar código, comentário e `docs/api-reference.md`. | Pendente |
| PLAT-C030 | 🟡 Importante | Correção | sem-rate-limit-em-endpoints-de-negocio | `LoginRateLimitingFilter.shouldNotFilter` (linhas 42-77) cobre apenas rotas de `/auth/**` e 2 de notificação. Nenhum endpoint de negócio é limitado — inclusive `GET /crm/customers/export`, que devolve a base inteira de clientes **sem paginação**, e `GET /estoque/movements`. Um token válido pode drenar a base em loop sem nenhum freio. | Pendente |
| PLAT-C031 | 🟢 Melhoria | Correção | grafana-anonimo-e-embedding-no-compose-hml | `docker-compose.yml` sobe o Grafana com `GF_AUTH_ANONYMOUS_ENABLED: true` (papel Viewer) e `GF_SECURITY_ALLOW_EMBEDDING: true`, publicado em `localhost:3002`. Qualquer um com acesso à rede vê as métricas operacionais sem autenticar. | Pendente |
| PLAT-C032 | 🟢 Melhoria | Correção | profile-default-dev-sem-bloqueio | `application.properties:10` — `spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`. Um deploy que esqueça a variável sobe em `dev`: H2 em memória, CORS `*`, CSP vazio, H2 console aberto e seed com senha do repositório. Há comentário de aviso, mas nenhum mecanismo impede. | Pendente |
| PLAT-C033 | 🟢 Melhoria | Correção | permissoes-orfas-role-e-permission-crud | `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` são criadas em V9/V36 mas **nenhum `@PreAuthorize` as usa** — o CRUD de role/permissão migrou para `DEV_ROLE_MANAGE`/`DEV_PERMISSION_MANAGE`. Também não constam de `ADMIN_PERMISSIONS` em `SeedConfig`/`DevRoleBootstrapConfig`. Ou são concedidas e não fazem nada, ou poluem a listagem de `GET /permissions`. Decidir entre remover ou voltar a usar. | Pendente |

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
