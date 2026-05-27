# security-spring

Template reutilizável de Spring Boot 4 com arquitetura hexagonal e camada de segurança completa: JWT, refresh token com rotação, RBAC + permissões granulares, verificação de email, rate limiting, bloqueio de conta e muito mais.

O objetivo é servir de base para novas aplicações — troque a lógica de negócio no `core/` sem tocar na infraestrutura de autenticação.

---

## Funcionalidades

### Autenticação
- **Access token** JWT (HS256) de curta duração configurável
- **Refresh token** opaco — armazenado como hash SHA-256 (nunca em texto claro no banco)
- **Rotação de refresh token** a cada uso — o token anterior é invalidado imediatamente
- **Detecção de reutilização** de refresh token → revoga todas as sessões do usuário e invalida todos os JWTs ativos
- **JWT blocklist**: logout invalida os JWTs existentes via threshold `iat` no Redis (sem lista explícita de tokens)
- **Autoregistro** com verificação de email (código de 6 dígitos via Resend.com, TTL configurável)
- **Reenvio de código** com cooldown por destinatário e proteção contra enumeração de usuários
- **Bloqueio de conta** após N tentativas de login malsucedidas (configurável)
- **Gerenciamento de sessões**: listar sessões ativas, revogar todas (logout total)
- **Limite de sessões simultâneas** por usuário (configurável)

### Autorização
- **RBAC com permissões granulares**: roles possuem conjuntos de permissões (`USER_CREATE`, `USER_READ`, `ROLE_MANAGE_PERMISSIONS`, etc.)
- Controle via `@PreAuthorize("hasAuthority('PERMISSAO')")` — stateless, sem sessão HTTP
- APIs de gerenciamento de roles e permissões em runtime

### Infraestrutura
- **Dev**: H2 em memória, Caffeine cache local, emails logados no console
- **Hml/Prod**: PostgreSQL + Flyway, Redis (cache distribuído de UserDetails)
- **Rate limiting** por IP em `POST /auth/login` (janela e limite configuráveis)
- **Scheduler distribuído** com ShedLock: limpeza diária de tokens expirados/revogados
- **Auditoria**: eventos de login, logout, criação/deleção de usuários, etc.
- **Security headers**: Content-Security-Policy configurável por perfil
- **Actuator**: health, info e metrics (Swagger desabilitado em hml/prod)
- **Docker**: `docker-compose.yml` (hml) e `docker-compose.prod.yml` (prod completo com rede isolada)

---

## Tecnologias

| Camada | Escolha |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.0.6 (Web, Security, Data JPA, Validation, Actuator, Cache) |
| Autenticação | JWT — JJWT 0.12.6 + Refresh Token opaco |
| Banco (dev) | H2 em memória (modo PostgreSQL) |
| Banco (hml/prod) | PostgreSQL 16 + Flyway (V1–V13) |
| Cache (dev) | Caffeine |
| Cache (hml/prod) | Redis 7 |
| Email | Resend.com (dev: stub que loga no console) |
| Scheduler | ShedLock (lock distribuído via banco) |
| Documentação | SpringDoc OpenAPI 3.0.2 (Swagger UI em dev) |
| Build | Maven 3.9 |
| Container | Docker + Docker Compose |
| Testes | JUnit 5, Mockito, Spring Security Test, Testcontainers |

---

## Arquitetura

O projeto segue arquitetura hexagonal (Ports & Adapters): o `core` não conhece frameworks, banco ou HTTP — apenas interfaces (`ports`).

```
┌─────────────────────────────────────────────────────────┐
│                       adapter/in                        │
│         Controllers · DTOs · Converters                 │
└────────────────────────────┬────────────────────────────┘
                             │  porta de entrada (UseCase)
┌────────────────────────────▼────────────────────────────┐
│                          core                           │
│     domain/model · domain/exception · ports · service   │
└────────────────────────────┬────────────────────────────┘
                             │  porta de saída (Repository/Port)
┌────────────────────────────▼────────────────────────────┐
│                       adapter/out                       │
│  persistence · jwt · email · redis · cache · security   │
└─────────────────────────────────────────────────────────┘

infra/
  config/     → SecurityConfig, BeanConfig, OpenApiConfig, SeedConfig
  security/   → filtro JWT, rate limiting, serviço de token, blocklist
  handler/    → GlobalExceptionHandler (ApiError padronizado)
  audit/      → listener de eventos de autenticação (AuditEvent)
  scheduler/  → RefreshTokenCleanupService (ShedLock)
  cli/        → ApplicationRunner para diagnóstico local
```

### Convenção de Lombok por camada

| Camada | Abordagem | Motivo |
|---|---|---|
| `core/domain/model` | Manual (sem Lombok) | Core independente de frameworks; modelos têm métodos semânticos (`confirmEmail`, `changePassword`) |
| `adapter/out/entities` | `@Getter @Setter @NoArgsConstructor` | Entidades JPA são infraestrutura |
| `adapter/in/dtos` | `@Data` | POJOs de transporte sem lógica |

---

## Segurança em detalhe

### Tokens
- Access token: JWT HS256, TTL 15 min (padrão). Leve — sem requisição ao banco a cada request
- Refresh token: 512 bits aleatórios → hash SHA-256 armazenado. O plaintext nunca toca o banco
- Logout: marca refresh como revogado + registra `blockAllBefore(username, now)` no Redis — JWTs com `iat` anterior são rejeitados

### Proteção contra reutilização de refresh token
Se um token já revogado for usado:
1. Todas as sessões do usuário são revogadas (`revokeAll`)
2. Todos os JWTs ativos são bloqueados (`blockAllBefore`)
3. Resposta: `401 REFRESH_TOKEN_REUSED`

### Bloqueio de conta
Após `AUTH_LOCKOUT_MAX_ATTEMPTS` falhas seguidas (padrão: 5), a conta é bloqueada por `AUTH_LOCKOUT_DURATION_MINUTES` minutos (padrão: 15). Resposta: `429 ACCOUNT_LOCKED` com header `Retry-After`.

### Rate limiting de login
Limitação por IP em `POST /auth/login`: máximo `LOGIN_RATE_MAX_REQUESTS` tentativas por janela de `LOGIN_RATE_WINDOW_SECONDS` segundos. Implementado em filtro de servlet com contadores em memória (dev) ou Redis (hml/prod).

---

## Setup local

### Pré-requisitos
- Java 21+
- Maven 3.9+ (ou o wrapper `./mvnw` incluso)
- Docker (apenas para perfis `hml` e `prod`)

### Dev — H2 em memória (padrão)

```bash
git clone https://github.com/seu-usuario/security-spring.git
cd security-spring
./mvnw spring-boot:run
```

O perfil `dev` é ativado automaticamente. Nenhuma variável de ambiente é obrigatória.

| Recurso | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| H2 Console | http://localhost:8080/h2-console |

> H2 Console — JDBC URL: `jdbc:h2:mem:demo` · Usuário: `sa` · Sem senha

Usuários de seed criados automaticamente:

| Usuário | Senha | Role | Permissões |
|---|---|---|---|
| `admin` | `Admin@dev1` | `ROLE_ADMIN` | Todas |
| `user` | `User@dev1` | `ROLE_USER` | `USER_READ` |

Para substituir as senhas de seed sem editar código:
```bash
SEED_ADMIN_PASSWORD=MinhaSenh@123 ./mvnw spring-boot:run
```

### Hml — PostgreSQL + Redis

```bash
# Sobe PostgreSQL (porta 5433) e Redis (porta 6380)
docker compose up -d

# Roda a aplicação no perfil hml
SPRING_PROFILES_ACTIVE=hml \
JWT_SECRET=sua-chave-de-256-bits-aqui \
DB_PASSWORD=postgres \
REDIS_PASSWORD=redis \
RESEND_API_KEY=re_sua_api_key \
./mvnw spring-boot:run
```

O Flyway aplica as migrations automaticamente (V1–V13). **Nenhum usuário é criado em hml** — use o script abaixo.

#### Criando o primeiro admin em hml/prod

```bash
# Gerar hash bcrypt da senha
python3 -c "import bcrypt; print(bcrypt.hashpw(b'SUA_SENHA', bcrypt.gensalt(10)).decode())"

# Inserir no banco (edite o hash e o email no arquivo antes)
psql $DB_URL -f scripts/create-first-admin.sql
```

### Prod — Docker Compose completo

```bash
# Configure as variáveis obrigatórias (veja tabela abaixo)
export DB_USERNAME=postgres DB_PASSWORD=... JWT_SECRET=... REDIS_PASSWORD=... CORS_ALLOWED_ORIGINS=https://meuapp.com RESEND_API_KEY=...

docker compose -f docker-compose.prod.yml up -d
```

O stack de prod inclui PostgreSQL, Redis e a aplicação em rede Docker isolada (`backend`), com healthchecks, restart automático e JVM tuned (`-XX:MaxRAMPercentage=75`, G1GC).

---

## Variáveis de ambiente

| Variável | Descrição | Default (dev) | Obrigatório em prod |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil (`dev`, `hml`, `prod`) | `dev` | ✅ |
| `JWT_SECRET` | Chave HMAC-256 (≥ 32 chars) | `dev-secret-...` | ✅ |
| `JWT_ACCESS_TTL_MINUTES` | TTL do access token (min) | `15` | — |
| `JWT_REFRESH_TTL_DAYS` | TTL do refresh token (dias) | `7` | — |
| `DB_URL` | JDBC URL PostgreSQL | — | ✅ |
| `DB_USERNAME` | Usuário do banco | — | ✅ |
| `DB_PASSWORD` | Senha do banco | — | ✅ |
| `REDIS_HOST` | Host do Redis | `localhost` | ✅ |
| `REDIS_PASSWORD` | Senha do Redis | — | ✅ |
| `CORS_ALLOWED_ORIGINS` | Origins permitidas no CORS | `*` | ✅ |
| `RESEND_API_KEY` | API key da Resend.com | `dev-placeholder` | ✅ |
| `RESEND_FROM` | Endereço de remetente | `noreply@example.com` | ✅ |
| `LOGIN_RATE_WINDOW_SECONDS` | Janela do rate limit | `60` | — |
| `LOGIN_RATE_MAX_REQUESTS` | Máx tentativas por janela | `10` (dev) / `5` (prod) | — |
| `AUTH_LOCKOUT_MAX_ATTEMPTS` | Tentativas antes do lockout | `5` | — |
| `AUTH_LOCKOUT_DURATION_MINUTES` | Duração do lockout (min) | `15` | — |
| `AUTH_MAX_SESSIONS_PER_USER` | Sessões simultâneas por usuário | `5` | — |
| `EMAIL_VERIFICATION_TTL_MINUTES` | TTL do código de verificação | `15` | — |
| `EMAIL_RESEND_COOLDOWN_SECONDS` | Cooldown para reenvio | `60` | — |
| `AUTH_REGISTRATION_DEFAULT_ROLES` | Roles no auto-registro | `` (nenhuma) | — |
| `SEED_ADMIN_PASSWORD` | Senha do admin seed (dev) | `Admin@dev1` | — |
| `SEED_USER_PASSWORD` | Senha do user seed (dev) | `User@dev1` | — |

> As variáveis com `Default (dev)` só têm default no perfil dev. Em `prod`, a aplicação falha no startup se as obrigatórias não estiverem definidas.

---

## Testes

```bash
./mvnw test
```

232 testes passando (unitários e de integração com H2). Os testes de integração com PostgreSQL real usam Testcontainers e requerem Docker + a variável `ENABLE_TC=true`:

```bash
ENABLE_TC=true ./mvnw test
```

---

## Documentação

A pasta `docs/` contém documentação detalhada por tema:

| Arquivo | Conteúdo |
|---|---|
| [architecture.md](docs/architecture.md) | Decisões de design e estrutura de pacotes |
| [security.md](docs/security.md) | Fluxo completo de autenticação e autorização |
| [flows.md](docs/flows.md) | Diagramas de sequência: login, refresh, logout, registro, troca de senha |
| [api-reference.md](docs/api-reference.md) | Todos os endpoints com exemplos de request/response |
| [configuration.md](docs/configuration.md) | Properties por categoria com valores e impacto |
| [domain-model.md](docs/domain-model.md) | Modelos de domínio e invariantes |
| [persistence.md](docs/persistence.md) | Schema do banco e migrations Flyway |
| [cli.md](docs/cli.md) | ApplicationRunner e comandos de diagnóstico |

O Swagger UI também está disponível em dev em http://localhost:8080/swagger-ui/index.html.

A coleção Postman `security-spring.postman_collection.json` (na raiz) cobre todos os endpoints com testes automatizados e variáveis de coleção (`access_token`, `refresh_token`, `created_user_id`, `created_role_name`).

---

## Como usar como template

1. **Fork ou clone** este repositório
2. **Renomeie o artefato**: altere `spring.application.name` em `application.properties` e `artifactId` em `pom.xml`
3. **Adicione seu domínio**: crie modelos em `core/domain/model/`, ports em `core/ports/` e services em `core/service/`
4. **Exponha seus endpoints**: adicione controllers em `adapter/in/controller/` e DTOs em `adapter/in/dtos/`
5. **Persista seu domínio**: crie entidades JPA em `adapter/out/persistence/entity/` e implemente os ports de saída em `adapter/out/persistence/`
6. **Adicione migrations**: crie `V14__...sql` e seguintes em `src/main/resources/db/migration/`

A camada de segurança (JWT, refresh token, RBAC, rate limiting, lockout, email verification) funciona integralmente sem modificações.
