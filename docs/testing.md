# Testes

## Visão geral

| Categoria | Quantidade | Tecnologia principal |
|-----------|-----------|----------------------|
| Unit tests | ~54 | JUnit 5 + Mockito |
| Integration tests (`*IT`) | 10 | JUnit 5 + Spring Boot Test + MockMvc |
| Testcontainers (PostgreSQL real) | 1 | Testcontainers + `@EnabledIfEnvironmentVariable` |
| ArchUnit (regras arquiteturais) | 1 | ArchUnit |

Total: ~82 arquivos de teste.

---

## Como rodar

```bash
# Todos os testes (unit + ITs com H2)
./mvnw test

# Somente o IT com PostgreSQL real (requer Docker)
ENABLE_TC=true ./mvnw test -Dtest=AuthFlowPostgresIT

# Classe específica
./mvnw test -Dtest=AuthServiceTest
```

> Os ITs (exceto `AuthFlowPostgresIT`) rodam com perfil `dev` — banco H2 in-memory, sem Redis, sem envio de email real.

---

## Categorias

### Unit tests

Testam uma classe isolada com dependências mockadas via Mockito. Não sobem o contexto Spring.

| Arquivo | O que cobre |
|---------|-------------|
| `AuthServiceTest` | Login, refresh, logout, rotação de token, detecção de reutilização (token theft); 2 casos para `security.2fa.required=true` (usuário sem TOTP → `TotpSetupRequiredException`; usuário com TOTP → challenge normal) |
| `UserServiceTest` | CRUD, troca de senha, fluxo pendingEmail, soft delete |
| `OAuthLoginServiceTest` | Resolução de usuário Google: novo / vincular conta existente / login direto |
| `TotpServiceTest` | Setup, confirm, disable, backup codes, fluxo challenge |
| `AvatarServiceTest` | Upload, delete, serve (local e S3) |
| `RoleServiceTest` | CRUD de roles, atribuição/remoção de permissions |
| `PermissionServiceTest` | CRUD de permissions |
| `StatsServiceTest` | Totais do dashboard |
| `AuditLogsServiceTest` | Delegação com filtros, sem filtros, página com entradas, página além do total |
| `SystemConfigServiceTest` | Leitura de feature flags, atualização, chave inexistente |
| `NotificationServiceTest` | `notify()` (retorna Notification salva), `getNotifications()`, `markAsRead()` com verificação de ownership (próprio/outro usuário/não encontrado), `markAllAsRead()`, `countUnread()`, `delete()` com ownership check (próprio/outro/não encontrado) |
| `NotificationPreferenceServiceTest` | `getPreferences()` retorna todos os tipos com defaults quando nada armazenado; preferência armazenada sobrepõe default; tipos sem preferência recebem default; `updatePreference()` persiste campos corretos |
| `JwtServiceTest` | Geração, validação, extração de claims, token expirado, assinatura inválida |
| `RefreshTokenServiceTest` | Hash, rotação, expiração, revogação |
| `JwtAuthenticationFilterTest` | Extração do Bearer, validação, blocklist check |
| `LoginRateLimitingFilterTest` | Sliding window por IP, endpoints cobertos, bypass de rotas não protegidas |
| `CustomUserDetailsServiceTest` | Cache de UserDetails, eviction |
| `GlobalExceptionHandlerTest` | Mapeamento de exceções de domínio para status HTTP |
| `RestHandlersTest` | 401 / 403 JSON responses |
| `PasswordPolicyTest` | Validação de complexidade de senha |
| `TraceIdFilterTest` | Injeção de traceId no MDC, extração de IP e User-Agent |
| `AesEncryptionAdapterTest` | Cifra/decifra AES-256-GCM do secret TOTP |
| `InMemoryTokenBlocklistAdapterTest` | Blocklist in-memory (perfil dev) |
| `InMemoryLoginRateLimiterAdapterTest` | Rate limiter in-memory (perfil dev) |
| `InMemoryLoginAttemptAdapterTest` | Lockout in-memory (perfil dev) |
| `RedisTokenBlocklistAdapterTest` | Blocklist Redis (hml/prod) com TTL |
| `RedisLoginRateLimiterAdapterTest` | Rate limiter Redis |
| `RedisLoginAttemptAdapterTest` | Lockout Redis |
| `RefreshTokenCleanupServiceTest` | Cron de limpeza de tokens expirados/revogados |
| `AuditLogCleanupServiceTest` | Cron de retenção de audit logs |
| `EmailVerificationCodeCleanupServiceTest` | Cron de limpeza de códigos expirados |
| `PasswordResetTokenCleanupServiceTest` | Cron de limpeza de tokens de reset expirados |
| `TotpChallengeCleanupServiceTest` | Cron de limpeza de challenge tokens |
| `TotpPendingSetupCleanupServiceTest` | Cron de limpeza de setups TOTP não confirmados |
| `DevChallengeCleanupServiceTest` | Cron de limpeza de dev_challenge_tokens expirados |
| `NotificationCleanupServiceTest` | Cron passa cutoff correto (90 dias por padrão), respeita retention-days configurável, delega ao repositório |
| `ThymeleafEmailRendererTest` | Renderiza cada um dos 5 templates com campos esperados; XSS escaping automático via `th:text` em valores maliciosos |
| `ResendEmailAdapterTest` | `sendVerificationCode` envia POST com from/to/subject/html corretos via `MockRestServiceServer`; `sendPasswordResetLink` verifica template e resetLink; falha HTTP lança `EmailDeliveryException`; `sendPasswordChangedAlert` e `sendTokenTheftAlert` verificam subject e template |
| `NotificationEventListenerTest` | Dispatch completo (persist + SSE + email) para PASSWORD_CHANGED e ACCOUNT_LOCKED; in-app desabilitado pula persistência e SSE mas envia email; email desabilitado persiste e faz push SSE mas pula email; falha na lookup de preferência faz fallback para defaults (todos habilitados); role_assigned inclui nome do papel no corpo; tipo de evento não mapeado é ignorado |
| `SseEmitterRegistryTest` | Register adiciona emitter; múltiplos emitters até o limite de 5; conexão além do limite é recusada; send para usuário sem emitters é no-op; remove diminui contagem; remove usuário inexistente é no-op; activeConnections retorna zero para usuário sem emitters |
| `GoogleTokenVerifierAdapterTest` | Validação de id_token Google (assinatura, issuer, audience) |
| `HmlStartupValidatorTest` | Boot fail em hml com variáveis ausentes |
| `ProdStartupValidatorTest` | Boot fail em prod com variáveis ausentes ou com valores padrão |
| `ActuatorSecurityTest` | Endpoints de actuator acessíveis apenas com auth |

Controladores (MockMvc com contexto parcial):

| Arquivo | O que cobre |
|---------|-------------|
| `AuthControllerTest` | Serialização/deserialização de requests e responses de auth |
| `RegistrationControllerTest` | Register, verify-email, resend-verification |
| `TotpControllerTest` | Endpoints `/auth/2fa/*` |
| `DevAuthControllerTest` | Endpoints `/auth/dev/*` (elevação de privilégio DEV, duplo TOTP) |
| `OAuthControllerTest` | Endpoints `/auth/oauth2/google` — happy path, cookie HttpOnly, OAuth desabilitado |
| `UserControllerTest` | CRUD de usuários, atribuição de roles |
| `RoleControllerTest` | CRUD de roles |
| `PermissionControllerTest` | CRUD de permissions |
| `AuditLogControllerTest` | Listagem filtrada de audit logs |
| `StatsControllerTest` | Endpoint de stats |
| `AvatarControllerTest` | Upload, delete, serve de avatar |
| `SystemConfigControllerTest` | GET /system/config/public, GET/PUT /system/config |
| `SystemInfoControllerTest` | GET /system/info — DEV_ELEVATED obrigatório |
| `NotificationControllerTest` | Lista paginada (+ `unreadOnly`), unread-count, markAsRead, markAllAsRead, delete, SSE stream (verifica registro no `SseEmitterRegistry`) |
| `NotificationPreferenceControllerTest` | GET preferências (lista completa e vazia), PUT com type válido (verifica delegação ao use case) e com type inválido (→ 400 `INVALID_ENUM_VALUE` com lista de valores) |

Adapters com contexto Spring parcial (cache/AOP):

| Arquivo | O que cobre |
|---------|-------------|
| `NotificationPreferenceRepositoryImplTest` | Comportamento real de `@Cacheable` (segunda chamada retorna do cache sem tocar o DB) e `@CacheEvict` (upsert invalida o cache, próxima leitura vai ao DB). Usa `ConcurrentMapCacheManager` em contexto minimal via `@ExtendWith(SpringExtension.class)` + `@EnableCaching`. `@BeforeEach` limpa o cache para evitar poluição entre testes no mesmo contexto. |
| `SystemConfigAdapterTest` | Comportamento real de `@Cacheable` em `findByKey()` e `getBoolean()`; `save()` evicta todo o cache (`allEntries=true`) — próxima leitura vai ao DB; caches são independentes por chave; `getBoolean` retorna `defaultValue` quando chave ausente. Mesmo padrão de contexto minimal que `NotificationPreferenceRepositoryImplTest`. |
| `S3AvatarStorageAdapterTest` | `save()` envia `PutObjectRequest` com bucket, key (`avatars/{filename}`), `contentType` correto (jpg→image/jpeg, png, webp, default→application/octet-stream) e `cacheControl`; retorna filename com extensão. `load()` retorna `Optional` com stream em caso de sucesso, `empty()` em `NoSuchKeyException` e em `S3Exception` genérica. `delete()` suprime `S3Exception` sem propagar. `save()` lança `IllegalStateException` em falha S3. `getPublicUrl()` monta URL com prefixo `avatars/`. |

---

### Integration tests (ITs)

Sobem o contexto Spring completo com MockMvc contra H2 in-memory (perfil `dev`), exceto onde indicado.

| Arquivo | O que cobre |
|---------|-------------|
| `AuthRegistrationFlowIT` | Fluxo completo: registro → verificação de email → login → refresh → logout |
| `AuthFlowSecurityIT` | Segurança de tokens: JWT expirado, refresh revogado, token theft detection (incluindo race condition concorrente), logout invalida sessions |
| `OAuthLoginFlowIT` | Login Google: novo usuário, vincular conta existente, login direto por google_id |
| `PasswordResetFlowIT` | Fluxo completo: forgot-password → reset-password → novas sessions revogadas |
| `AuditEventsIT` | Eventos de auditoria persistidos corretamente para login, logout, registro, RBAC |
| `UserProfileAndSessionsTest` | GET /users/me, PATCH /users/me, GET /users/me/sessions, DELETE session |
| `VerifyEmailConcurrencyIT` | Race condition: duas requisições simultâneas com o mesmo código ativam a conta exatamente uma vez (valida o `markAsUsed()` atômico via CAS) |
| `TotpFlowIT` | End-to-end do fluxo TOTP: ativar 2FA → login com challenge → completar com código TOTP real → receber tokens |
| `NotificationFlowIT` | Fluxo completo de notificações: register → verify-email → login → troca de senha (dispara `PASSWORD_CHANGED` async) → GET /notifications → mark-as-read → DELETE. Segundo teste cobre markAllAsRead zerando o unread-count. |
| `SystemConfigFlowIT` | GET /system/config/public (sem auth → 200); GET /system/config sem auth → 401, com DEV_ELEVATED → 200; PUT chave inválida → 400, sem auth → 401; PUT chave pública persiste e aparece no getPublicConfig; toggle maintenance mode via `SystemConfigPort` direto (bypassa whitelist de PUBLIC_KEYS) evicta `@CacheEvict(allEntries=true)` → `MaintenanceModeFilter` retorna 503 em paths não-allowlistados; `/system/config/public` permanece acessível durante manutenção. |

---

### Testcontainers (PostgreSQL real)

`AuthFlowPostgresIT` é o único teste que sobe um container PostgreSQL real via Testcontainers. Ele valida que:
- As migrations Flyway executam sem erro contra PostgreSQL (não apenas H2)
- O fluxo de login/refresh/logout funciona com o banco de produção

**Por que está desabilitado por padrão:** sobe um container Docker e é mais lento. Precisa do daemon Docker disponível.

```bash
# Ativar explicitamente
ENABLE_TC=true ./mvnw test -Dtest=AuthFlowPostgresIT
```

A anotação `@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")` garante que não seja executado em pipelines de CI padrão.

---

### Segurança específica

Testes que validam comportamento de autorização independentemente do fluxo de negócio:

| Arquivo | O que cobre |
|---------|-------------|
| `PermissionControllerSecurityTest` | 401 sem auth, 403 com role insuficiente, 200/201 com permission correta |
| `RoleControllerSecurityTest` | 401 sem auth, 403 sem permissão, guard DEV_ELEVATED em assign/removePermission para `DEV_ROLE_MANAGE`/`DEV_PERMISSION_MANAGE` |
| `AuditLogControllerSecurityTest` | 401 sem auth, 403 sem `AUDIT_READ`, 200 com permissão correta |
| `StatsControllerSecurityTest` | 401 sem auth, 403 com apenas uma das permissões exigidas (`USER_READ` + `ROLE_READ`), 200 com ambas |
| `UserControllerSecurityTest` | Proteção dos endpoints de usuário por permission |
| `ExpiredJwtTest` | JWT expirado retorna 401 |
| `InvalidSignatureJwtTest` | JWT com assinatura inválida retorna 401 |
| `AuthRateLimitingTest` | Rate limit por IP bloqueia após N tentativas |
| `MaintenanceModeFilterTest` | Modo manutenção retorna 503; `/actuator/health/**` e `/system/config/public` são passados; filtro respeita `security.maintenance.enabled=false` |
| `NotificationControllerSecurityTest` | GET /notifications, GET /notifications/unread-count, PATCH /{id}/read, PATCH /read-all, DELETE /{id}, GET /stream — todos retornam 401 sem autenticação |
| `NotificationPreferenceControllerSecurityTest` | GET /notifications/preferences e PUT /notifications/preferences/{type} retornam 401 sem autenticação |

---

### ArchUnit

`HexagonalArchitectureTest` verifica em tempo de teste que as regras de dependência da arquitetura hexagonal não foram violadas:

- `core/domain` e `core/ports` — proibido qualquer `org.springframework.*`
- `core/service` — permite apenas `org.springframework.transaction.*` (exceção consciente: `@Transactional` no use-case boundary); todo o resto do Spring é barrado
- `adapter/in.controller` não acessa `adapter/out` (e vice-versa)
- `adapter/in.controller` não acessa `core/ports/out` diretamente (rule scoped a controllers — `adapter/in/sse/` pode implementar ports de saída)
- `adapter/` não acessa `core/service/` — sempre via interfaces dos ports
- Services implementam apenas interfaces de `core/ports/in` ou `core/ports/out`
- Classes de `adapter/in/dtos` não podem ser referenciadas em `core/service` (isolamento de DTO)
- `infra/notification` não importa `adapter/in/dtos` — listeners não usam DTOs de resposta HTTP
- `adapter/in/controller` não importa `infra/notification` — controllers usam `NotificationSsePort` (port), não o registry concreto

Se um desenvolvedor importar Spring MVC, Spring Data ou Spring Security no core, este teste falha imediatamente no `./mvnw test`.

---

## Helpers de teste

Disponíveis apenas com `@Profile("dev")` — não são incluídos no build de produção.

### `EmailVerificationTestHelper`

Recupera o código de verificação em texto puro do `LoggingEmailAdapter`. Necessário porque o banco armazena apenas o SHA-256, não o código original.

```java
@Autowired EmailVerificationTestHelper verificationHelper;

String code = verificationHelper.getCodeForUsername("testuser");
// usar `code` na chamada ao endpoint POST /auth/verify-email
```

### `RefreshTokenTestHelper`

Manipula refresh tokens via JDBC para simular cenários de expiração sem esperar o TTL real.

```java
@Autowired RefreshTokenTestHelper refreshTokenTestHelper;

refreshTokenTestHelper.expireTokenByHash(tokenHash);
// próximo POST /auth/refresh com esse hash retorna 400 REFRESH_TOKEN_EXPIRED
```

### `SeedCredentials`

Constantes de credenciais do seed de desenvolvimento (`SeedConfig`). Centralizadas aqui para evitar literais espalhados nos testes.

### `TestHashUtils`

Utilitário de teste para computar SHA-256 de tokens e comparar com o que foi persistido, sem depender de `TokenHashUtils` de produção diretamente nos asserts dos testes.

---

## Convenções

- Nomes de métodos em snake_case descrevendo comportamento: `login_com_senha_incorreta_retorna_401`
- ITs com `@DirtiesContext` quando o estado do H2 pode contaminar outros testes (ex: `VerifyEmailConcurrencyIT`)
- Mocks de `EmailPort` para evitar tentativas reais de envio em unit tests
- `@ActiveProfiles("dev")` em todos os ITs — garante H2, Caffeine e LoggingEmailAdapter
