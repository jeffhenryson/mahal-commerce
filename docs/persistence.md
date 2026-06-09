# Persistência

## Entidades JPA (`adapter/out/persistence/entity/`)

### UserEntity — tabela `users`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | UNIQUE NOT NULL |
| password | VARCHAR | nullable (BCrypt hash; null para usuários Google sem senha local) |
| enabled | BOOLEAN | NOT NULL |
| email | VARCHAR | UNIQUE (armazenado em lowercase) |
| email_verified | BOOLEAN | NOT NULL |
| pending_email | VARCHAR | nullable (email aguardando confirmação) |
| avatar_filename | VARCHAR | nullable |
| created_at | TIMESTAMP | nullable |
| auth_provider | VARCHAR(20) | NOT NULL, default `LOCAL` (`LOCAL` ou `GOOGLE`) |
| google_id | VARCHAR(255) | nullable, índice parcial único `idx_users_google_id WHERE google_id IS NOT NULL` |
| deleted_at | TIMESTAMP | nullable (soft delete) |

Relacionamento: M2M com `RoleEntity` via tabela `user_roles (user_id, role_id)`

---

### RoleEntity — tabela `roles`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| name | VARCHAR | UNIQUE NOT NULL |

Relacionamento: M2M com `PermissionEntity` via tabela `role_permissions (role_id, permission_id)`

---

### PermissionEntity — tabela `permissions`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| name | VARCHAR | UNIQUE NOT NULL |

---

### RefreshTokenEntity — tabela `refresh_tokens`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| user_id | BIGINT | FK → users(id) ON DELETE CASCADE |
| token_hash | VARCHAR(128) | UNIQUE NOT NULL (SHA-256 do token) |
| expires_at | TIMESTAMP | NOT NULL |
| revoked | BOOLEAN | NOT NULL, default false |
| created_at | TIMESTAMP | NOT NULL default now() |
| rotated_at | TIMESTAMP | nullable |
| ip_address | VARCHAR(45) | nullable — IPv4 ou IPv6 do cliente no login |
| user_agent | VARCHAR(512) | nullable — truncado a 512 chars |

Índices: `uk_refresh_token_hash`, `idx_refresh_user (user_id)`, `idx_refresh_expires (expires_at)`

O token plaintext **nunca** é persistido. `TokenHashUtils.sha256()` é aplicado antes de salvar e antes de buscar.

Queries relevantes em `RefreshTokenJpaRepository`:
- `findActiveByUsername(username, now)` — sessões ativas de um usuário (não expiradas, não revogadas)
- `countActiveByUsername(username, now)` — contagem para enforçar limite por usuário
- `countAllActive(now)` — **contagem global** usada pelo `ActiveSessionsMetric` (Gauge Prometheus)

---

### EmailVerificationCodeEntity — tabela `email_verification_codes`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| code | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do código) |
| expires_at | TIMESTAMP | NOT NULL |
| used | BOOLEAN | NOT NULL, default false |
| sent_at | TIMESTAMP | NOT NULL |

Índices: `(code)`, `idx_email_verification_codes_expires_at (expires_at)` (para cleanup scheduler)

O código plaintext **nunca** é persistido.

---

### PasswordResetTokenEntity — tabela `password_reset_tokens`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do token) |
| expires_at | TIMESTAMPTZ | NOT NULL |
| requested_at | TIMESTAMPTZ | NOT NULL default now() |
| used_at | TIMESTAMPTZ | nullable — null = não usado |

Índices: `uk_prt_token_hash (token_hash)`, `idx_prt_token_hash (token_hash)`, `idx_password_reset_tokens_expires_at (expires_at)`

O token plaintext **nunca** é persistido.

---

### TotpConfigEntity — tabela `totp_config`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | UNIQUE NOT NULL |
| secret_enc | TEXT | NOT NULL (secret AES-256 cifrado) |
| enabled | BOOLEAN | NOT NULL, default false |
| confirmed_at | TIMESTAMPTZ | nullable — null = setup pendente, não-null = 2FA ativo |
| created_at | TIMESTAMPTZ | NOT NULL default now() |

`enabled=false` até que o usuário confirme o primeiro código via `POST /auth/2fa/confirm`.  
Setups não confirmados são removidos pelo `TotpPendingSetupCleanupService` após `totp.pending-setup.ttl-hours` (padrão 24h).

---

### TotpBackupCodeEntity — tabela `totp_backup_codes`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| code_hash | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do código) |
| used_at | TIMESTAMPTZ | nullable — null = disponível |

Índices: `uk_totp_backup_code (code_hash)`, `idx_totp_backup_username (username)`

Cada usuário tem 8 backup codes. Todos são deletados e recriados ao chamar `POST /auth/2fa/backup-codes/regenerate`.

---

### TotpChallengeTokenEntity — tabela `totp_challenge_tokens`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| token_hash | VARCHAR(64) | UNIQUE NOT NULL (SHA-256 do token) |
| expires_at | TIMESTAMPTZ | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL default now() |
| used_at | TIMESTAMPTZ | nullable — null = não usado |

Índices: `uk_totp_challenge_token (token_hash)`, `idx_totp_challenge_hash (token_hash)`, `idx_totp_challenge_tokens_expires_at (expires_at)`

TTL padrão: 5 minutos. Tokens expirados são removidos pelo `TotpChallengeCleanupService`.

---

### AuditLogEntity — tabela `audit_logs`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| action | VARCHAR(80) | NOT NULL (EventType como string) |
| target | VARCHAR(255) | nullable — "user:x", "role:y", "permission:z" |
| details | TEXT | nullable — JSON string com detalhes extras |
| ip_address | VARCHAR(45) | nullable |
| timestamp | TIMESTAMPTZ | NOT NULL default now() |

Índices: `idx_audit_logs_username (username)`, `idx_audit_logs_action (action)`, `idx_audit_logs_timestamp (timestamp DESC)`, `idx_audit_logs_username_timestamp (username, timestamp DESC)`

Retenção configurável via `audit.retention-days` (padrão 365 dias). Limpeza pelo `AuditLogCleanupService`.

---

### NotificationEntity — tabela `notifications`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| username | VARCHAR(80) | NOT NULL |
| type | VARCHAR(50) | NOT NULL (valor do enum `NotificationType`) |
| title | VARCHAR(255) | NOT NULL |
| body | TEXT | nullable |
| read_at | TIMESTAMPTZ | nullable — null = não lida |
| created_at | TIMESTAMPTZ | NOT NULL |

Índices: `idx_notifications_username (username)`, `idx_notifications_username_read_at (username, read_at)` para queries de não-lidas por usuário, `idx_notifications_created_at (created_at)` para ordenação paginada.

FK: `fk_notifications_username → users(username) ON DELETE CASCADE` (V42) — garante limpeza automática ao deletar usuário.

Limpeza automática via `NotificationCleanupService` (cron 04:00 AM): deleta notificações lidas com `read_at < cutoff`. Threshold configurável via `notification.read.retention-days` (padrão 90 dias).

---

### NotificationPreferenceEntity — tabela `notification_preferences`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| username | VARCHAR(80) | PK (composta com type) |
| type | VARCHAR(50) | PK (composta com username) — valor do enum `NotificationType` |
| in_app_enabled | BOOLEAN | NOT NULL DEFAULT TRUE |
| email_enabled | BOOLEAN | NOT NULL DEFAULT TRUE |

Chave primária composta `(username, type)` garante no máximo uma preferência por usuário/tipo. Linha ausente = ambos habilitados (default). O `NotificationEventListener` consulta preferências antes de persistir notificação ou enviar email.

Implementado com `@IdClass(NotificationPreferenceEntity.PreferenceId)` no JPA.

Índices: `idx_notification_preferences_username (username)` (V42) — evita full table scan em `findByUsername`.  
FK: `fk_notification_prefs_username → users(username) ON DELETE CASCADE` (V42).

`upsert()` é implementado com `INSERT ... ON CONFLICT (username, type) DO UPDATE SET ...` (SQL nativo) em vez do padrão check-then-act, garantindo atomicidade sob requisições concorrentes.

---

### SystemConfigEntity — tabela `system_config`

| Coluna | Tipo | Constraint |
|--------|------|-----------|
| id | BIGINT | PK, auto-increment |
| config_key | VARCHAR(255) | UNIQUE NOT NULL |
| config_value | TEXT | nullable |
| updated_by | VARCHAR(255) | nullable — username de quem atualizou por último |
| updated_at | TIMESTAMP | nullable |

Chaves pré-populadas pela `V34__system_config.sql` e `V38__add_system_config_new_flags.sql`:

| Chave | Valor padrão | Descrição |
|-------|-------------|-----------|
| `auth.registration.enabled` | `true` | Habilita/desabilita auto-registro público |
| `auth.google.enabled` | `true` | Habilita/desabilita login via Google OAuth |
| `auth.google.register.enabled` | `true` | Habilita/desabilita criação de conta via Google |
| `auth.forgot-password.enabled` | `true` | Habilita/desabilita fluxo de recuperação de senha |
| `security.maintenance.enabled` | `false` | Modo manutenção — retorna 503 a todos exceto `/actuator/health/**` e `/system/config/public` |
| `security.2fa.required` | `false` | Força 2FA obrigatório: usuários sem TOTP ativo recebem 403 `TOTP_SETUP_REQUIRED` ao tentar logar |
| `module.audit-logs.enabled` | `true` | Quando `false`, o `AuditEventListener` não persiste eventos — útil para ambientes de teste ou modo degradado |
| `module.roles.enabled` | `true` | Quando `false`, os endpoints `/roles/**` e `/permissions/**` retornam 503 `MODULE_DISABLED` |

As chaves listadas acima são **públicas** (acessíveis via `GET /system/config/public` sem autenticação). Alterações requerem `DEV_ELEVATED` e são persistidas imediatamente no banco.

**Cache:** `SystemConfigAdapter` usa `@Cacheable` (cache `systemConfig`) em `findByKey()` e `getBoolean()`. O `save()` invalida todo o cache via `@CacheEvict(allEntries=true)`. O TTL é controlado por `spring.cache.caffeine.spec` (dev) ou `spring.cache.redis.time-to-live` (hml/prod). Leituras pelo `MaintenanceModeFilter` (a cada request) beneficiam-se deste cache.

---

## Repositórios

Cada port OUT tem uma implementação `*RepositoryImpl` que:
1. Injeta a interface Spring Data JPA (`*JpaRepository`)
2. Injeta o converter de domínio ↔ entidade
3. Implementa os métodos do port

```
core/ports/out/*Repository
    ↑ implementado por
adapter/out/persistence/repository/*RepositoryImpl
    ↑ usa
adapter/out/persistence/repository/*JpaRepository  (Spring Data)
adapter/out/persistence/entity/*Entity
adapter/out/persistence/converter/*EntityConverter  (domínio ↔ entidade)
```

`UserEntityConverter`: converte entre `User` (domínio) e `UserEntity` (JPA), incluindo roles/permissions aninhados.

---

## Banco de dados por perfil

| Perfil | Banco | Migrations |
|--------|-------|-----------|
| `dev` | H2 in-memory | Schema criado automaticamente pelo JPA (sem Flyway) |
| `hml` | PostgreSQL | Flyway (`V1__init.sql` … `V43__`) |
| `prod` | PostgreSQL | Flyway (`V1__init.sql` … `V43__`) |

Em hml/prod não há seed automático — usuário admin deve ser criado via CLI (`create-admin`).

### Histórico completo de migrations (V1–V42)

#### V1–V12 — Schema base e segurança inicial

| Migration | Descrição |
|-----------|-----------|
| `V1__init.sql` | Cria tabelas `users` (id, username, password), `roles` e `user_roles`. Índices em `users(username)` e `roles(name)`. |
| `V2__refresh_tokens.sql` | Cria tabela `refresh_tokens` com `token_hash` (SHA-256), `expires_at`, `revoked`, `rotated_at`. Índices em `token_hash`, `user_id` e `expires_at`. |
| `V3__seed_admin_hml.sql` | Insere `ROLE_ADMIN` e `ROLE_USER` como dados base. **Não cria usuário admin** — o admin deve ser criado via CLI `create-admin`. O nome do arquivo é enganoso; as roles são pré-requisito para as migrations seguintes. |
| `V4__permissions.sql` | Cria tabelas `permissions` e `role_permissions`. Insere permissões base (`USER_CREATE`, `USER_READ`, `USER_DELETE`, `USER_ROLE_ASSIGN`). `ROLE_ADMIN` recebe todas; `ROLE_USER` recebe apenas `USER_READ`. |
| `V5__remove_unused_user_update_permission.sql` | Remove `USER_UPDATE` (sem endpoint correspondente naquele momento). Revertida em V8. |
| `V6__add_user_enabled.sql` | Adiciona coluna `enabled BOOLEAN NOT NULL DEFAULT TRUE` em `users`. |
| `V7__add_user_status_permission.sql` | Insere permissão `USER_STATUS` para controle de enable/disable de conta, separando semanticamente de `USER_DELETE`. |
| `V8__add_user_update_permission.sql` | Readiciona `USER_UPDATE` após `PATCH /users/{id}` ser implementado. |
| `V9__add_role_and_permission_management_permissions.sql` | Insere permissões de gestão de RBAC: `ROLE_READ/CREATE/DELETE/MANAGE_PERMISSIONS` e `PERMISSION_READ/CREATE/DELETE`. Todas atribuídas ao `ROLE_ADMIN`. |
| `V10__email_verification.sql` | Adiciona `email` (VARCHAR 254) e `email_verified` em `users`. Cria tabela `email_verification_codes`. Índice parcial único em `users(email) WHERE email IS NOT NULL`. |
| `V11__shedlock.sql` | Cria tabela `shedlock` — exigida pelo ShedLock para coordenação de schedulers em múltiplas instâncias (k8s, ECS). |
| `V12__hash_email_verification_code.sql` | Aumenta coluna `code` para VARCHAR(64) para acomodar hash SHA-256 em base64url. Adiciona `sent_at` para cooldown de reenvio. |

#### V13–V29 — Features incrementais

| Migration | Descrição |
|-----------|-----------|
| `V13__refresh_token_device_info.sql` | Adiciona `ip_address` (VARCHAR 45) e `user_agent` (VARCHAR 512) em `refresh_tokens` — rastreia origem das sessões. |
| `V14__password_reset_tokens.sql` | Cria tabela `password_reset_tokens` com hash SHA-256, TTL e flag `used_at`. |
| `V15__pending_email.sql` | Adiciona `pending_email` em `users` — suporta o fluxo de troca de email com confirmação no novo endereço. |
| `V16__totp.sql` | Cria `totp_config`, `totp_backup_codes` e `totp_challenge_tokens`. |
| `V17__add_junction_indexes.sql` | Índices reversos nas tabelas de junção: `idx_rp_permission_id ON role_permissions(permission_id)` e `idx_ur_role_id ON user_roles(role_id)`. A PK composta cobre buscas pelo primeiro campo; estes índices cobrem buscas pelo segundo ("quais roles têm a permission X?", "quais users têm a role Y?"). |
| `V18__audit_logs.sql` | Cria tabela `audit_logs` com índices em username, action e timestamp. |
| `V19__audit_read_permission.sql` | Insere permissão `AUDIT_READ` e a atribui ao `ROLE_ADMIN`. |
| `V20__audit_logs_range_index.sql` | Índice composto `(username, timestamp DESC)` para queries com filtro de usuário + período. |
| `V21__add_totp_config_created_at.sql` | Adiciona `created_at` em `totp_config` (para cleanup de setups pendentes por data). |
| `V22__add_user_avatar.sql` | Adiciona coluna `avatar_filename` em `users`. |
| `V23__add_user_created_at.sql` | Adiciona coluna `created_at` em `users`. |
| `V24__soft_delete_users.sql` | Adiciona coluna `deleted_at` em `users` (soft delete). |
| `V25__add_oauth_google.sql` | Adiciona `auth_provider` e `google_id` em `users`; remove NOT NULL de `password`. |
| `V26__normalize_email_lowercase.sql` | Normaliza emails existentes para `lowercase + strip`. **Atenção em tabelas grandes:** o `UPDATE` bloqueia a tabela `users` durante a execução. Para zero-downtime em produção com muitos registros, considere rodar o UPDATE em batches fora do Flyway antes de aplicar a migration. |
| `V27__add_ttl_indexes.sql` | Índices em `expires_at` de `refresh_tokens`, `email_verification_codes` e `password_reset_tokens` — melhora performance dos schedulers de cleanup. |
| `V28__add_totp_challenge_ttl_index.sql` | Índice em `totp_challenge_tokens(expires_at)` para o scheduler de limpeza de challenges. |
| `V29__add_google_id_index.sql` | Índice parcial único `idx_users_google_id ON users(google_id) WHERE google_id IS NOT NULL` — otimiza lookup por `sub` do token Google. |
| `V30__dev_challenge_tokens.sql` | Cria tabela `dev_challenge_tokens` para os tokens de desafio DEV (duplo TOTP). Colunas: `id`, `username`, `token_hash` (SHA-256, único), `period_t` (período TOTP T), `expires_at`, `created_at`, `used_at`. Índices em `token_hash` (lookup) e `expires_at` (scheduler de cleanup). |
| `V31__add_users_enabled_index.sql` | Índice parcial `idx_users_enabled ON users(enabled) WHERE deleted_at IS NULL` — melhora queries de `GET /users?enabled=false` em tabelas grandes. |
| `V32__remove_orphaned_permissions_from_role_admin.sql` | Remove as permissões `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` de `ROLE_ADMIN`. Essas permissões foram atribuídas ao ROLE_ADMIN em V9, mas os endpoints correspondentes (`POST /roles`, `DELETE /roles`, `POST /permissions`, `DELETE /permissions`) exigem `DEV_ROLE_MANAGE`/`DEV_PERMISSION_MANAGE` — exclusivas de ROLE_DEV. Ter essas permissões em ROLE_ADMIN criava uma falsa expectativa (admin as via no banco mas recebia 403 ao tentar usá-las). |
| `V33__remove_unused_dev_permissions.sql` | Remove as permissões `DEV_LOGS_TECHNICAL`, `DEV_SYSTEM_CONFIG` e `DEV_DEBUG_ENDPOINTS` do banco. Criadas pelo `DevRoleBootstrapConfig` para uso futuro, mas nenhum endpoint as referenciava. Removidas também do bootstrap para não serem recriadas. |
| `V34__system_config.sql` | Cria tabela `system_config` com colunas `config_key` (UNIQUE NOT NULL), `config_value`, `updated_by` e `updated_at`. Insere 4 chaves iniciais (`auth.registration.enabled`, `auth.google.enabled`, `auth.google.register.enabled`, `auth.forgot-password.enabled`), todas com valor `true`. Usada pelo `SystemConfigController` (`GET/PUT /system/config`) para controle de feature flags em runtime sem restart. |
| `V35__add_totp_config_cleanup_index.sql` | Índice parcial `idx_totp_config_cleanup ON totp_config(enabled, created_at) WHERE enabled = false` — melhora o DELETE do `TotpPendingSetupCleanupService`, que filtra exatamente por `enabled=false AND created_at < :before`. Sem este índice o DELETE faz full scan conforme a tabela cresce. |
| `V36__add_dev_role_permissions.sql` | Insere no banco as permissões exclusivas de `ROLE_DEV`: `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE`, `PERMISSION_DELETE`, `DEV_ROLE_MANAGE`, `DEV_PERMISSION_MANAGE` (via `ON CONFLICT DO NOTHING`). O `DevRoleBootstrapConfig` ainda as cria em código de forma idempotente; esta migration torna o schema explícito e rastreável no histórico Flyway. |
| `V37__cleanup_dev_role.sql` | Garante `ROLE_DEV` no schema independente do bootstrap (`INSERT … ON CONFLICT DO NOTHING`). Remove os grants de `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE` de `ROLE_DEV` — essas permissões não são verificadas por nenhum `@PreAuthorize`; os endpoints usam `DEV_ROLE_MANAGE` e `DEV_PERMISSION_MANAGE`. |
| `V38__add_system_config_new_flags.sql` | Insere 4 novas chaves em `system_config` (idempotente via `ON CONFLICT DO NOTHING`): `security.maintenance.enabled` (modo manutenção — retorna 503 a todos exceto `/actuator/health/**`), `security.2fa.required` (força 2FA para todos os usuários no login), `module.audit-logs.enabled` (habilita/desabilita persistência de logs de auditoria), `module.roles.enabled` (habilita/desabilita os endpoints de RBAC `/roles` e `/permissions`). Valor padrão: `false` para maintenance e 2fa; `true` para os módulos. |
| `V39__remove_orphaned_permissions.sql` | Remove do banco as permissões `ROLE_CREATE`, `ROLE_DELETE`, `PERMISSION_CREATE` e `PERMISSION_DELETE`. Foram inseridas pela V36 como "dados de referência" mas nenhum `@PreAuthorize` as usa — os endpoints de RBAC verificam `DEV_ROLE_MANAGE` e `DEV_PERMISSION_MANAGE`. Mantê-las visíveis via `GET /permissions` criava falsa expectativa. V37 já havia removido os grants de `ROLE_DEV`; esta migration remove as próprias permissões. Remove também os grants residuais de `role_permissions` antes de deletar. |
| `V40__notifications.sql` | Cria a tabela `notifications` (módulo de notificações in-app). Campos: `id BIGSERIAL`, `username VARCHAR(80)`, `type VARCHAR(50)`, `title VARCHAR(255)`, `body TEXT`, `read_at TIMESTAMPTZ` (nullable — `NULL` = não lida), `created_at TIMESTAMPTZ`. Três índices: por `username` (listagem por usuário), por `(username, read_at)` (contagem de não-lidas e markAllAsRead), por `created_at` (cleanup por TTL via `NotificationCleanupService`). |
| `V41__notification_preferences.sql` | Cria a tabela `notification_preferences` com PK composta `(username, type)` e flags `in_app_enabled`/`email_enabled` (ambas `DEFAULT TRUE`). Linha ausente = preferências padrão (ambas ativas). Permite desativar notificação in-app e/ou email por tipo individualmente via `PUT /notifications/preferences/{type}`. |
| `V42__notification_indexes_and_fk.sql` | Adiciona índice `idx_notification_preferences_username ON notification_preferences(username)` — evita full table scan em `findByUsername`. Adiciona FK `fk_notifications_username → users(username) ON DELETE CASCADE` e `fk_notification_prefs_username → users(username) ON DELETE CASCADE` — garante integridade referencial e limpeza automática de notificações ao deletar usuário. |
| `V43__add_notification_read_at_index.sql` | Índice parcial `idx_notifications_read_at ON notifications(read_at) WHERE read_at IS NOT NULL` — melhora o DELETE do `NotificationCleanupService`, que filtra por `read_at IS NOT NULL AND read_at < :cutoff`. Sem este índice a query faz full scan conforme a tabela cresce. |

---

## Estratégia de paginação

### Problema: N+1 em relações M2M com paginação

`UserEntity` e `RoleEntity` têm relações `@ManyToMany(fetch = FetchType.LAZY)`. Numa paginação ingênua com `findAll(Pageable)`, o Spring Data executa:

1. `SELECT * FROM users LIMIT ?` — retorna 20 usuários
2. Para cada usuário: `SELECT * FROM user_roles WHERE user_id = ?` — 20 queries extras
3. Para cada role: `SELECT * FROM role_permissions WHERE role_id = ?` — N queries adicionais

Resultado: 1 query visível no log, mas dezenas executadas em background.

### Padrão ID-first + JOIN FETCH

Todos os repositórios com relações M2M usam o mesmo padrão de duas fases:

**Fase 1 — Buscar apenas IDs com paginação**

```sql
SELECT u.id FROM users u ORDER BY u.id LIMIT ? OFFSET ?
```

Essa query retorna apenas `Long` — sem joins, sem lazy loading, sem overhead de coluna.

**Fase 2 — Carregar entidades completas com JOIN FETCH pelos IDs**

```sql
SELECT DISTINCT u FROM UserEntity u
  LEFT JOIN FETCH u.roles r
  LEFT JOIN FETCH r.permissions
  WHERE u.id IN (:ids)
  ORDER BY u.id
```

Um único `JOIN FETCH` traz usuários, roles e permissões em uma só query. O `DISTINCT` evita duplicatas do produto cartesiano.

**Resultado:** 2 queries no total, independente de quantos usuários ou relações existam na página.

### Onde é aplicado

| Repositório | Método |
|-------------|--------|
| `UserRepositoryImpl.findAll()` | `findAllIds()` → `findAllWithRolesByIdIn()` |
| `UserRepositoryImpl.findFiltered()` | `findFilteredIds()` (Criteria API) → `findAllWithRolesByIdIn()` |
| `RoleRepositoryImpl.findAll()` | `findAllIds()` → `findAllWithPermissionsByIdIn()` |
| `RoleRepositoryImpl.findByNameContaining()` | `findIdsByNameContaining()` → `findAllWithPermissionsByIdIn()` |

### `findFiltered` e a Criteria API

`UserRepositoryImpl.findFiltered()` usa a Criteria API do JPA em vez de JPQL porque os filtros (`search`, `enabled`) são opcionais e o JPQL com `WHERE :param IS NULL OR campo = :param` causa erro de tipo no PostgreSQL quando o parâmetro é `null`. A Criteria API constrói os predicados dinamicamente apenas quando os filtros estão presentes.

### Campos de ordenação permitidos

`GET /users?sortBy=createdAt&sortDir=desc` aceita somente campos da whitelist para evitar injeção de nome de coluna:

```
id | username | email | enabled | createdAt
```

Qualquer valor fora da lista faz o sort cair para `id ASC` (default seguro).

---

## Schedulers de cleanup (`infra/scheduler/`)

Todos usam **ShedLock** para garantir execução em apenas uma instância.

| Scheduler | Cron padrão (property) | O que remove | ShedLock |
|-----------|------------------------|--------------|----------|
| `RefreshTokenCleanupService` | `0 0 3 * * *` (`refresh-token.cleanup.cron`) | Refresh tokens expirados ou revogados | lockAtMostFor PT55M |
| `PasswordResetTokenCleanupService` | `0 15 3 * * *` (`password-reset.cleanup.cron`) | Tokens de reset expirados | lockAtMostFor PT30M |
| `EmailVerificationCodeCleanupService` | `0 30 3 * * *` (`email-verification.cleanup.cron`) | Códigos de verificação expirados | lockAtMostFor PT30M |
| `TotpChallengeCleanupService` | `0 30 3 * * *` (`totp.challenge.cleanup.cron`) | Challenge tokens expirados | lockAtMostFor PT15M |
| `TotpPendingSetupCleanupService` | `0 45 3 * * *` (`totp.pending-setup.cleanup.cron`) | Configs TOTP não confirmadas > TTL | lockAtMostFor PT30M |
| `AuditLogCleanupService` | `0 45 3 * * *` (`audit.cleanup.cron`) | Audit logs mais antigos que `audit.retention-days` | lockAtMostFor PT55M |
