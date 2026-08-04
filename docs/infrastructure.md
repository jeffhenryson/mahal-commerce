# Infraestrutura

**Última atualização deste doc:** 2026-07-27

Onde o backend roda, do que ele depende e o que cada ambiente muda. Complementa:

- [`docs/architecture.md`](architecture.md) — decisões arquiteturais e topologia de deploy dos frontends.
- [`docs/persistence.md`](persistence.md) — banco por perfil, dimensionamento HikariCP, schedulers de cleanup.
- [`docs/security.md`](security.md) — filtros, JWT, CORS, headers, rate limit de login.

Cada domínio documenta o recorte de infra que **ele** usa na seção `## Segurança e
Infraestrutura` do seu README em [`docs/dominios/`](dominios/).

## Ambientes e profiles

Três profiles Spring. O default é `dev` (`application.properties:10`,
`spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}`). Um deploy sem `SPRING_PROFILES_ACTIVE`
ainda sobe em `dev`, mas não mais em silêncio: `DevStartupValidator` aborta o boot se o perfil
`dev` for ativado sobre sinais de infraestrutura remota — `DB_URL` apontando para um banco
não-local que o perfil `dev` ignoraria, ou `CORS_ALLOWED_ORIGINS` com origem remota. O escape
hatch `DEV_ALLOW_REMOTE_INFRA=true` cobre quem aponta o ambiente local para um banco
compartilhado de propósito. Resolvido em PLAT-C032.

| | `dev` | `hml` | `prod` |
|---|---|---|---|
| Banco | H2 in-memory (`MODE=PostgreSQL`), `ddl-auto=create-drop` | Postgres 16 | Postgres 16 |
| Flyway | desabilitado | habilitado, `ddl-auto=none` | habilitado, `ddl-auto=none` |
| Cache | Caffeine (`maximumSize=500,expireAfterWrite=60s`) | Redis, TTL 60s | Redis, TTL 60s |
| Redis | autoconfiguração **excluída** | obrigatório | obrigatório, `ssl.enabled=true` |
| Rate limit de login | 10 req / 60s (in-memory) | 10 req / 60s (Redis) | **5** req / 60s (Redis) |
| CORS origins | `*`, credentials `false` | `${CORS_ALLOWED_ORIGINS:https://example.com}` | `${CORS_ALLOWED_ORIGINS}` sem default, credentials `true` |
| CSP | vazio (desabilitado, por causa do Swagger) | `default-src 'none'; frame-ancestors 'none'` | idem |
| Actuator | mesma porta da app, exige `DEV_ELEVATED` | porta **8081**, isolada por rede | porta **8081**, isolada por rede |
| H2 console | `/h2-console` habilitado | desabilitado | desabilitado |
| E-mail | `logging` (só loga) | `resend` | `resend` |
| Avatar storage | `local` | `local` | `s3` |
| `forward-headers-strategy` | `none` | `native` | `native` |
| Validador de boot | — | `HmlStartupValidator` | `ProdStartupValidator` |

`hml` e `prod` recusam subir com segredo ausente ou placeholder — ver
[Variáveis de ambiente](#variáveis-de-ambiente-e-segredos).

## Imagem

`Dockerfile` multi-stage:

1. `maven:3.9-eclipse-temurin-21` — `dependency:go-offline` antes de copiar `src/` (cache de
   camada), depois `mvn package -DskipTests`.
2. `eclipse-temurin:21-jre-alpine` — cria grupo/usuário `spring`, `chown` de `/app` e
   `/app/uploads`, e **roda como não-root** (`USER spring`).

`ENTRYPOINT` respeita `JAVA_OPTS` (em prod: `-XX:MaxRAMPercentage=75 -XX:+UseG1GC`).

> `EXPOSE 8080` no Dockerfile é herança — a app escuta em `${PORT:8082}`
> (`application.properties`). `EXPOSE` é declarativo e não afeta o runtime; os composes mapeiam
> a porta real.

## Containers

### `docker-compose.yml` — hml local

Todos os serviços usam sufixo `-mahal` e a network externa `cerne-commerce_default`,
compartilhada com os repositórios de frontend.

| Serviço | Imagem | Porta host | Papel |
|---|---|---|---|
| `postgres-mahal` | `postgres:16-alpine` | 5435 | banco `security`; volume externo `security-spring_pgdata_hml` |
| `redis-mahal` | `redis:7-alpine` | 6382 | `--requirepass`; rate limit, lockout, token blocklist e cache |
| `prometheus-mahal` | `prom/prometheus:v2.52.0` | 9092 | retenção 7d |
| `grafana-mahal` | `grafana/grafana:11.0.0` | 3002 | dashboards e alertas provisionados |
| `mahal-backend` | build local | 8082 (app), 8083→8081 (actuator) | profile `hml` |

> ⚠️ Grafana sobe com `GF_AUTH_ANONYMOUS_ENABLED: true` (viewer) e
> `GF_SECURITY_ALLOW_EMBEDDING: true`. Ver PLAT-C031.

> ⚠️ Existe um terceiro compose fora deste repositório
> (`~/Documents/myprojects/mahaltabacaria/`) que é o que **efetivamente** cria os containers do
> ambiente local. Ver PLAT-C024.

### `docker-compose.prod.yml` — produção

`postgres` e `redis` (com `--save 60 1`) ficam na network interna `backend`, **sem portas
publicadas**. O edge é **Traefik** (network `traefik-public`), com TLS via
`certresolver=myresolver`:

| Serviço | Host |
|---|---|
| `app` | `api.mahaltabacaria.com.br` \|\| `api.mahalcommerce.com` |
| `mahal-commerce-ui` (admin) | `admin.mahaltabacaria.com.br` \|\| `admin.mahalcommerce.com` |
| `mahal-commerce-ui-market` | `mahaltabacaria.com.br` \|\| `www.mahaltabacaria.com.br` |

Não há nginx no projeto — todo roteamento e terminação TLS é do Traefik.

`docker-compose.prod.yml` exige `TOTP_ENCRYPTION_KEY` sem fallback — o default versionado no
repositório foi removido em PLAT-C028, e ambas as chaves que já estiveram hardcoded estão
blacklistadas no `ProdStartupValidator`, de modo que copiá-las do histórico do git de volta
para o `.env` também não sobe.

## Datastores

**Postgres 16 + Flyway** — 61 migrations (`V1`–`V61`) em
`src/main/resources/db/migration/`. Em `hml`/`prod` o Hibernate não altera schema
(`ddl-auto=none`); em `dev` não há Flyway e o schema vem do `create-drop`, por isso permissões
semeadas por migration **não existem em dev** (são supridas por `SeedConfig` e
`DevRoleBootstrapConfig`). Detalhes de modelagem em [`docs/persistence.md`](persistence.md).

**HikariCP** — pool 10, min-idle 2, connection-timeout 30s, max-lifetime 30min
(`application.properties`, tudo sobrescrevível por env). Proporção 5:1 com
`server.tomcat.threads.max=50`; o dimensionamento definitivo é PLAT-C025.

**Redis 7** (`hml`/`prod`) — quatro usos, todos com adapter in-memory equivalente em `dev`:

| Uso | Adapter Redis | Adapter dev |
|---|---|---|
| Rate limit de login | `RedisLoginRateLimiterAdapter` (sliding window via script Lua atômico) | `InMemoryLoginRateLimiterAdapter` |
| Lockout de conta | `RedisLoginAttemptAdapter` | `InMemoryLoginAttemptAdapter` |
| Blocklist de token | `RedisTokenBlocklistAdapter` | in-memory |
| Cache Spring (`userDetails`, `users`, `userAuthorities`) | `spring.cache.type=redis` | Caffeine |

## Storage de arquivos

Avatares, selecionados por `avatar.storage.type` (`infra/config/S3StorageConfig.java`,
`@ConditionalOnProperty`):

- `local` → `LocalAvatarStorageAdapter`, grava em `./uploads/avatars` (default em `dev`/`hml`).
- `s3` → `S3AvatarStorageAdapter` (default em `prod`).

O bucket do Terraform tem versionamento, criptografia server-side, *public access block* e
lifecycle, e é servido por **CloudFront + OAC** (`terraform/s3.tf`, `terraform-lite/s3.tf`).

Limites de upload: `spring.servlet.multipart.max-file-size=3MB` e o limite de negócio
`avatar.max-size-bytes=2MB` (`infra/config/AvatarProperties.java`), com validação por **magic
bytes** — não por `Content-Type` (`core/service/AvatarService.java`).

## Processamento assíncrono

**Não há fila nem broker** (sem RabbitMQ, Kafka, SQS, AMQP ou JMS). O assíncrono é feito com
`ThreadPoolTaskExecutor` (`infra/config/AsyncConfig.java`):

| Executor | core | max | fila | Uso |
|---|---|---|---|---|
| `emailTaskExecutor` | 2 | 5 | 100 | envio de e-mail |
| `taskExecutor` (default) | 2 | 10 | 200 | auditoria, notificações |

Push em tempo real: **SSE** (`GET /notifications/stream`, `adapter/in/sse/SseEmitterRegistry`),
com limite de conexões por usuário — ver [`docs/security.md`](security.md).

**8 jobs agendados** em `infra/scheduler/`, com pool de 4 threads
(`spring.task.scheduling.pool.size=4`) e **ShedLock** (`infra/config/ShedLockConfig.java`,
tabela `shedlock` da V11) para não duplicarem em execução multi-instância:
`AuditLogCleanupService`, `RefreshTokenCleanupService`, `PasswordResetTokenCleanupService`,
`TotpChallengeCleanupService`, `TotpPendingSetupCleanupService`, `DevChallengeCleanupService`,
`EmailVerificationCodeCleanupService`, `NotificationCleanupService`.

## E-mail

Adapter escolhido por `email.provider` em `adapter/out/email/EmailAdapterConfig.java`:

| Valor | Adapter | Onde é default |
|---|---|---|
| `resend` | `ResendEmailAdapter` (REST `api.resend.com/emails`) | `hml`, `prod` |
| `logging` | `LoggingEmailAdapter` (só loga, não envia) | `dev` |
| — | `MailpitEmailAdapter` | dev/testes, sob demanda |

Templates em `src/main/resources/templates/`, renderizados por `ThymeleafEmailRenderer`.

## Observabilidade

- **Actuator** — `hml`/`prod` expõem `health`, `info`, `prometheus` (e `metrics` em `hml`) na
  porta **8081**, isolada por rede; em `dev` fica na mesma porta e exige `DEV_ELEVATED`.
  Health groups `liveness`/`readiness` (`readiness` inclui `db` e `redis`).
- **Prometheus** — `prometheus.yml` faz scrape de `mahal-backend:8081/actuator/prometheus` a
  cada 15s, retenção 7d.
- **Grafana** — dashboards, datasources e alertas provisionados em `grafana/provisioning/`.
- **Trace** — `TraceIdFilter` popula o MDC e devolve `X-Trace-Id` (exposto por CORS); o mesmo id
  aparece no corpo de erro `ApiError`.
- Histograma de percentis de latência HTTP habilitado em `hml`/`prod`
  (`management.metrics.distribution.percentiles-histogram.http.server.requests=true`).

As métricas de segurança (`auth.rate_limit.blocked.total`, counters e gauges) estão listadas em
[`docs/security.md`](security.md).

## CI/CD

`.github/workflows/ci.yml`, dois jobs em `ubuntu-latest`:

1. **`build-test`** — JDK 21, build + testes (incluindo os de integração com
   Testcontainers/PostgreSQL), publica o relatório JaCoCo, builda a imagem Docker **uma vez** e a
   sobe como artifact (`docker save | gzip`).
2. **`deploy-ecr`** — baixa o artifact, `docker load`, autentica na AWS via **OIDC** (sem chave
   estática), faz push para o ECR e atualiza o serviço ECS.

A refatoração de build único (C015) ainda não foi validada com um push real — PLAT-C027.

## Infraestrutura como código

Dois sabores em Terraform, mutuamente exclusivos:

| | `terraform/` | `terraform-lite/` |
|---|---|---|
| Compute | ECS Fargate + ALB + autoscaling | EC2 single-node (`user_data.sh`) |
| Banco | RDS | RDS |
| Cache | ElastiCache | — |
| Segredos | Secrets Manager (`secrets.tf`) | SSM Parameter Store (`ssm.tf`, `random_password`) |
| Registry | ECR | — |
| Logs | CloudWatch | — |
| Comum | VPC, security groups, IAM, S3 | idem |

## Variáveis de ambiente e segredos

Referência em `.env.example` (o `.env` real é gitignored). As sensíveis:

| Variável | Onde é lida | Validada no boot? |
|---|---|---|
| `JWT_SECRET` | `jwt.secret` → `JwtService` | ✅ `prod`: rejeita < 44 chars, `dev-secret`, `troque-para` |
| `JWT_ISSUER` / `JWT_AUDIENCE` | `JwtService` (`requireIssuer`/`requireAudience`) | ✅ `prod`: rejeita os defaults |
| `TOTP_ENCRYPTION_KEY` | `AesEncryptionAdapter` (AES-256-GCM) | ✅ `prod`: rejeita ausência, placeholders, < 44 chars e **as duas** chaves já hardcoded no repositório |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | datasource | ✅ `prod`: rejeita `localhost` e `h2:mem` |
| `REDIS_PASSWORD` / `REDIS_HOST` | Redis | — |
| `RESEND_API_KEY` / `RESEND_FROM` | `ResendEmailAdapter` | ✅ `prod`: rejeita placeholder |
| `INFINITEPAY_HANDLE` | `InfinitePayAdapter` (ECM-F004) | ✅ `prod`: rejeita ausência/placeholder — sem API key: `/links`/`payment_check` não exigem uma |
| `GOOGLE_CLIENT_ID` | `OAuthConfig` | ✅ `prod`: obrigatório |
| `CORS_ALLOWED_ORIGINS` | `CorsConfigurationSource` | ✅ `prod`: rejeita `*`; o bean rejeita `*` + credentials |
| `AVATAR_BASE_URL` / `AVATAR_S3_*` | `AvatarProperties`, `S3StorageConfig` | ✅ `hml`/`prod`: rejeita ausente, `localhost`, `example.com` |
| `DEV_EMAIL` / `DEV_PASSWORD` | `DevRoleBootstrapConfig` | ✅ `prod`: rejeita a senha de exemplo |
| `SEED_ADMIN_PASSWORD` / `SEED_USER_PASSWORD` | `SeedConfig` (**só profile `dev`**) | n/a |

Em AWS, os valores vêm de Secrets Manager (`terraform/`) ou SSM Parameter Store
(`terraform-lite/`), nunca do `.env`.

> ⚠️ Os 5 segredos movidos para `.env` em C001 **não foram rotacionados** — continuam no
> histórico do git. Ver PLAT-C023.
>
> Enquanto produção não subir, `JWT_SECRET`, `TOTP_ENCRYPTION_KEY` e `REDIS_PASSWORD` podem ser
> rotacionados ao custo de gerar novos valores (`openssl rand -base64 32`) e reiniciar — não há
> dado cifrado a migrar. Depois que existirem usuários com 2FA ativo, trocar
> `TOTP_ENCRYPTION_KEY` passa a exigir reencriptação dos secrets TOTP, senão todos perdem o
> segundo fator. `RESEND_API_KEY` e `GOOGLE_CLIENT_ID` dependem dos consoles de terceiros em
> qualquer cenário.
