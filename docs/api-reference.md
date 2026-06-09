# API Reference — security-spring

**Base URL (dev):** `http://localhost:8080` 
**Auth:** `Authorization: Bearer <accessToken>` em todos os endpoints, exceto os marcados como **Público**.

> Swagger UI disponível em `http://localhost:8080/swagger-ui.html`

---

## Formato de erro padrão

Todos os erros retornam `ApiError`:

```json
{
  "message": "Mensagem legível",
  "errorCode": "SNAKE_CASE_CODE",
  "timestamp": "2026-05-30T16:00:00Z",
  "path": "/auth/login",
  "traceId": "uuid-para-correlação-de-log"
}
```

### Tabela de error codes

| errorCode | HTTP | Quando ocorre |
|-----------|------|---------------|
| `INVALID_CREDENTIALS` | 401 | Username/senha errados ou conta desabilitada |
| `ACCOUNT_LOCKED` | 401 | Conta bloqueada por tentativas excessivas |
| `ACCESS_DENIED` | 403 | Token válido mas sem a permissão necessária |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh token inválido |
| `REFRESH_TOKEN_EXPIRED` | 401 | Refresh token expirado — redirecionar para login |
| `REFRESH_TOKEN_REUSED` | 401 | Token já usado — possível roubo, todas as sessões encerradas |
| `TOTP_CHALLENGE_EXPIRED` | 401 | Challenge de 2FA expirou (5 min) |
| `INVALID_TOTP_CODE` | 400 | Código TOTP ou backup code inválido |
| `TOTP_CODE_REQUIRED` | 400 | Operação requer código 2FA mas ele não foi enviado (usuário tem 2FA ativo) |
| `TOTP_NOT_CONSECUTIVE` | 400 | Segundo código DEV não pertence ao período T+1 do primeiro |
| `DEV_CHALLENGE_EXPIRED` | 410 | devToken DEV expirou (TTL 90s) ou já foi consumido |
| `TOTP_ALREADY_ENABLED` | 409 | 2FA já está ativo |
| `TOTP_NOT_ENABLED` | 400 | Operação requer 2FA ativo |
| `TOTP_SETUP_REQUIRED` | 403 | Login bloqueado: `security.2fa.required=true` e o usuário ainda não ativou 2FA |
| `INVALID_PASSWORD` | 400 | Senha atual incorreta |
| `PASSWORD_RESET_TOKEN_INVALID` | 400 | Token de reset inválido |
| `PASSWORD_RESET_TOKEN_EXPIRED` | 400 | Token de reset expirado |
| `USERNAME_ALREADY_EXISTS` | 409 | Username já cadastrado |
| `EMAIL_ALREADY_EXISTS` | 409 | Email já cadastrado |
| `EMAIL_ALREADY_VERIFIED` | 409 | Email já verificado |
| `VERIFICATION_CODE_INVALID` | 400 | Código de verificação de email inválido |
| `VERIFICATION_CODE_EXPIRED` | 400 | Código de verificação expirado |
| `USER_NOT_FOUND` | 404 | Usuário não encontrado |
| `ROLE_NOT_FOUND` | 404 | Role não encontrada |
| `PERMISSION_NOT_FOUND` | 404 | Permissão não encontrada |
| `SESSION_NOT_FOUND` | 404 | Sessão não encontrada |
| `ROLE_ALREADY_EXISTS` | 409 | Role já existe |
| `PERMISSION_ALREADY_EXISTS` | 409 | Permissão já existe |
| `OAUTH_TOKEN_INVALID` | 401 | Token Google inválido, expirado ou audience incorreto |
| `AVATAR_TOO_LARGE` | 400 | Arquivo de avatar excede 2 MB |
| `INVALID_AVATAR_FORMAT` | 400 | Formato não suportado — aceito JPEG, PNG, WebP |
| `VALIDATION_ERROR` | 400 | Campos inválidos (bean validation) |
| `UNREADABLE_BODY` | 400 | Body ausente ou JSON malformado |
| `EMAIL_DELIVERY_FAILED` | 503 | Falha ao enviar email |
| `INTERNAL_ERROR` | 500 | Erro interno inesperado |

---

## Auth — `/auth`

### POST /auth/login — Público

```json
// Request
{ "username": "string", "password": "string" }

// Response 200 — login completo (sem 2FA)
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}

// Response 200 — 2FA ativado (precisa de verificação)
{
  "status": "PENDING_2FA",
  "challengeToken": "string",
  "expiresInSeconds": 300
}
```

**Cookie:** `refreshToken` HttpOnly setado em `Path=/auth`, `Max-Age=604800` (7 dias), `SameSite=Strict`.  
O `refreshToken` também vem no body como fallback para clientes que não lêem cookies.  
Usar `withCredentials: true` nas chamadas a `/auth/*` para que o browser envie o cookie automaticamente.

**Erros:** `401 INVALID_CREDENTIALS`, `401 ACCOUNT_LOCKED`, `429` (rate-limit — header `Retry-After: <seg>`)

---

### POST /auth/2fa/verify — Público · Rate-limited

Completa o login quando 2FA está ativo. Usar o `challengeToken` recebido no `/login`.

```json
// Request
{
  "challengeToken": "string",
  "code": "123456"  // TOTP 6 dígitos OU backup code formato XXXX-XXXX-XXXX
}

// Response 200 → TokenPairResponse (igual ao login sem 2FA)
```

**Erros:** `400 INVALID_TOTP_CODE`, `401 TOTP_CHALLENGE_EXPIRED`, `429` rate-limit

---

### POST /auth/refresh — Público

Rotaciona o refresh token e emite novo par de tokens. Aceita cookie ou body.

```json
// Request (opcional — usa cookie automaticamente se omitido)
{ "refreshToken": "string" }

// Response 200 → TokenPairResponse
```

**Erros:** `401 INVALID_REFRESH_TOKEN`, `401 REFRESH_TOKEN_EXPIRED`, `401 REFRESH_TOKEN_REUSED`

> Ao receber `REFRESH_TOKEN_REUSED`, todas as sessões do usuário são invalidadas por suspeita de roubo de token. Redirecionar para login.

---

### POST /auth/logout — Público

```json
// Request (opcional — usa cookie se omitido)
{ "refreshToken": "string" }

// Response 204
```

Limpa o cookie `refreshToken` na resposta mesmo que o token não exista.

---

### DELETE /auth/sessions — Autenticado

Revoga **todas** as sessões do usuário logado (logout total).

```
// Response 204
```

---

### GET /auth/sessions — Autenticado

Lista as sessões ativas do usuário logado.

```json
// Response 200
[
  {
    "id": 1,
    "createdAt": "2026-05-30T10:00:00Z",
    "expiresAt": "2026-06-06T10:00:00Z",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0..."
  }
]
```

---

### DELETE /auth/sessions/{id} — Autenticado

Revoga uma sessão específica do usuário logado.

```
// Response 204 / 404 SESSION_NOT_FOUND
```

---

### POST /auth/forgot-password — Público

Inicia o fluxo de recuperação de senha. Sempre retorna 204 (sem disclosure de email).

```json
{ "email": "string" }
// Response 204
```

---

### POST /auth/reset-password — Público

```json
{
  "token": "string",          // token recebido por email
  "newPassword": "string"     // deve respeitar PasswordPolicy
}
// Response 204
```

**Erros:** `400 PASSWORD_RESET_TOKEN_INVALID`, `400 PASSWORD_RESET_TOKEN_EXPIRED`, `400 VALIDATION_ERROR`

---

### POST /auth/confirm-email-change — Público

Confirma a troca de email usando o código enviado ao novo endereço.

```json
{ "code": "ABC123DEF456" }   // exatamente 12 chars [A-Z0-9]
// Response 204 / 400 VERIFICATION_CODE_INVALID / 400 VERIFICATION_CODE_EXPIRED
```

---

## Registration — `/auth`

### POST /auth/register — Público

```json
// Request
{
  "username": "string",   // 3–80 chars, obrigatório
  "password": "string",   // PasswordPolicy, obrigatório
  "email": "string"       // email válido, max 254 chars, obrigatório
}
// Response 201
```

Conta criada com `enabled=false`. Email de verificação enviado automaticamente.  
**Erros:** `409 USERNAME_ALREADY_EXISTS`, `409 EMAIL_ALREADY_EXISTS`, `400 VALIDATION_ERROR`

---

### POST /auth/verify-email — Público

```json
{ "code": "ABC123DEF456" }   // 12 chars [A-Z0-9]
// Response 204 — ativa a conta (enabled=true, emailVerified=true)
```

**Erros:** `400 VERIFICATION_CODE_INVALID`, `400 VERIFICATION_CODE_EXPIRED`, `409 EMAIL_ALREADY_VERIFIED`

> `409 EMAIL_ALREADY_VERIFIED` é retornado quando o email da conta associada ao código já foi verificado. Isso permite ao frontend distinguir "reload após verificação bem-sucedida" (conta já está ativa — pode redirecionar ao login) de "código inválido" (400 — usuário digitou código errado).

---

### POST /auth/resend-verification — Público

```json
{ "email": "string" }
// Response 204 (sempre, sem disclosure). Cooldown 60s por email.
```

---

## OAuth — `/auth/oauth2`

### POST /auth/oauth2/google — Público

Login ou cadastro via conta Google. O frontend obtém um `id_token` usando o [Google Identity Services](https://developers.google.com/identity/gsi/web) e envia ao backend para validação.

```json
// Request
{ "idToken": "eyJ..." }   // id_token retornado pelo Google Sign-In

// Response 200 → TokenPairResponse (igual ao login com senha)
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Cookie:** mesmo comportamento do `POST /auth/login` — `refreshToken` HttpOnly em `Path=/auth`.  
Usar `withCredentials: true` para que o browser armazene o cookie.

**Comportamento no servidor:**

| Situação | O que acontece |
|----------|----------------|
| Google ID (`sub`) já vinculado a uma conta | Login direto na conta existente |
| Email do Google já existe em conta local | Google ID vinculado automaticamente — login na conta local |
| Email não existe | Nova conta criada com `ROLE_USER`, `emailVerified=true`, sem senha |

> Usuários criados via Google **não têm senha** e não podem usar `POST /auth/forgot-password` nem `PUT /users/me/password`. O `authProvider` deles é `GOOGLE`.

**Erro:** `401 OAUTH_TOKEN_INVALID` — token inválido, expirado ou `aud` não corresponde ao `GOOGLE_CLIENT_ID` configurado.

**No Angular (exemplo básico com Google Identity Services):**
```typescript
google.accounts.id.initialize({
  client_id: environment.googleClientId,
  callback: async ({ credential }) => {
    const res = await http.post('/auth/oauth2/google',
      { idToken: credential },
      { withCredentials: true }
    ).toPromise();
    // res = TokenPairResponse
  }
});
```

---

## 2FA TOTP — `/auth/2fa`

Todos os endpoints abaixo requerem `Authorization: Bearer <accessToken>`.

### GET /auth/2fa/status

```json
// Response 200
{
  "enabled": true,
  "backupCodesRemaining": 5   // 0 quando enabled=false
}
```

---

### POST /auth/2fa/setup

Inicia o setup de 2FA. Retorna o segredo e o URI para gerar o QR code.

```json
// Response 200
{
  "secret": "BASE32SECRET",
  "otpauthUri": "otpauth://totp/security-spring:username?secret=...&issuer=security-spring"
}
```

O frontend deve renderizar o `otpauthUri` como QR code (ex: biblioteca `qrcode`).  
**Erro:** `409 TOTP_ALREADY_ENABLED`

---

### POST /auth/2fa/confirm

Confirma o setup escaneando o QR e enviando o primeiro código.

```json
// Request
{ "code": "123456" }   // exatamente 6 dígitos

// Response 200
{
  "backupCodes": ["ABCD-1234-EF56", "..."]  // 8 códigos, guardar agora
}
```

**Erro:** `400 INVALID_TOTP_CODE`

> Os backup codes são exibidos **uma única vez**. O frontend deve orientar o usuário a salvá-los antes de fechar o modal.

---

### DELETE /auth/2fa

Desativa o 2FA.

```json
// Request
{
  "currentPassword": "string",
  "code": "123456"   // TOTP 6 dígitos OU backup code XXXX-XXXX-XXXX
}
// Response 204
```

**Erros:** `400 INVALID_PASSWORD`, `400 INVALID_TOTP_CODE`, `400 TOTP_NOT_ENABLED`

---

### POST /auth/2fa/replace

Troca o dispositivo 2FA: valida o código do app atual, apaga a configuração vigente e inicia um novo setup. Confirmar com `POST /auth/2fa/confirm` para ativar o novo dispositivo.

```json
// Request
{ "currentTotpCode": "123456" }   // código atual do app (6 dígitos)

// Response 200
{
  "secret": "BASE32SECRET",
  "otpauthUri": "otpauth://totp/..."
}
```

**Erros:** `400 INVALID_TOTP_CODE`, `400 TOTP_NOT_ENABLED`

---

### POST /auth/2fa/backup-codes/regenerate

Gera novos backup codes (invalida os anteriores).

```json
// Request
{ "currentPassword": "string" }

// Response 200
{ "backupCodes": ["ABCD-1234-EF56", "..."] }  // 8 novos códigos
```

**Erros:** `400 INVALID_PASSWORD`, `400 TOTP_NOT_ENABLED`

---

## DEV Elevation — `/auth/dev`

Fluxo de elevação de privilégio para a área de desenvolvedor via **duplo TOTP consecutivo**. Exige que o usuário tenha `ROLE_DEV` e 2FA ativo. O token resultante contém a authority `DEV_ELEVATED`, que protege endpoints sensíveis como `/actuator/**`.

> O access token DEV-elevado **não tem refresh token** — expira em 1h e não pode ser renovado. Novo duplo TOTP necessário após expirar.

### POST /auth/dev/first-code — Bearer + `ROLE_DEV`

Etapa 1: valida o código atual do app autenticador e reserva o período T. Retorna um `devToken` temporário (TTL 90s) para ser usado na etapa 2.

```json
// Request
{ "totpCode": "123456" }   // exatamente 6 dígitos

// Response 200
{
  "devToken": "opaque-token-base64url",
  "expiresIn": 90   // segundos até o devToken expirar
}
```

**Erros:** `400 INVALID_TOTP_CODE`, `403 ACCESS_DENIED` (sem `ROLE_DEV`)

> Após receber o `devToken`, o frontend deve aguardar o próximo ciclo de 30s do app TOTP antes de prosseguir para a etapa 2.

---

### POST /auth/dev/complete — Público

Etapa 2: valida que o segundo código pertence ao período T+1 (imediatamente consecutivo ao T registrado na etapa 1). Emite o access token DEV-elevado com TTL de 1h.

```json
// Request
{
  "devToken": "opaque-token-base64url",   // obtido na etapa 1
  "totpCode": "654321"                    // novo código do próximo período
}

// Response 200
{
  "accessToken": "eyJ...",   // JWT com DEV_ELEVATED + todas as authorities ROLE_DEV
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Erros:** `400 TOTP_NOT_CONSECUTIVE`, `400 INVALID_TOTP_CODE`, `410 DEV_CHALLENGE_EXPIRED`

> `DEV_CHALLENGE_EXPIRED` (410) ocorre quando o `devToken` expirou (após 90s) ou já foi usado. O frontend deve reiniciar pelo `POST /auth/dev/first-code`.

---

## Users — `/users`

> **Dois formatos de resposta de usuário:**
> - `UserProfileResponse` — retornado por `GET /users/me` e `PATCH /users/me`. Inclui `pendingEmail`.
> - `UserResponse` — retornado pelos demais endpoints (`GET /users`, `GET /users/{id}`, `POST /users`, `PATCH /users/{id}`). Inclui `avatarUrl` e `createdAt`, mas **não** inclui `pendingEmail`.

### GET /users/me — Autenticado

```json
// Response 200 → UserProfileResponse
```

---

### PATCH /users/me — Autenticado

Atualiza username e/ou email do próprio perfil.

```json
// Request
{
  "username": "string",       // 3–80 chars, obrigatório
  "email": "string",          // email válido, min 1 char, max 254, opcional (null = não altera)
  "currentPassword": "string" // obrigatório SOMENTE ao trocar email
}
// Response 200 → UserProfileResponse
```

**Fluxo de troca de email:**  
- A conta **não** é desabilitada.  
- `UserProfileResponse.pendingEmail` recebe o novo email.  
- Um código é enviado ao novo endereço — confirmar via `POST /auth/confirm-email-change`.  
- Enquanto pendente: `.email` = email atual, `.pendingEmail` = novo email.  
- Frontend pode usar `pendingEmail != null` para exibir banner "confirme seu novo e-mail".

**Erros:** `409 USERNAME_ALREADY_EXISTS`, `409 EMAIL_ALREADY_EXISTS`, `400 INVALID_PASSWORD`

---

### POST /users/me/avatar — Autenticado

Faz upload do avatar. Enviar como `multipart/form-data`, campo `file`.

```
Content-Type: multipart/form-data
file: <binary>

// Response 200
{ "avatarUrl": "http://localhost:8080/avatars/f47ac10b-uuid.jpg" }
```

**Limites de tamanho:**
- Conteúdo do arquivo: máximo **2 MB** (validado no service → `AVATAR_TOO_LARGE`)
- Boundary multipart: máximo **3 MB** (limite do servidor → `413 Payload Too Large` antes de chegar ao controller)

**Validação de formato:** feita por magic bytes (não por extensão ou `Content-Type`). Formatos aceitos: JPEG, PNG, WebP.  
**Erros:** `400 AVATAR_TOO_LARGE`, `400 INVALID_AVATAR_FORMAT`

> Ao fazer upload quando já existe um avatar, o arquivo anterior é deletado automaticamente no servidor.

---

### DELETE /users/me/avatar — Autenticado

Remove o avatar do usuário.

```
// Response 204
```

---

### GET /avatars/{filename} — **Público**

Serve o arquivo de avatar. Sem autenticação. Filename gerado pelo servidor (UUID).

```
// Response 200  Content-Type: image/jpeg | image/png | image/webp
//               Cache-Control: max-age=31536000, immutable
// Response 404  arquivo não encontrado ou filename inválido (contém ..)
```

> Como os filenames são UUIDs aleatórios, não há enumeração. Use sempre a `avatarUrl` retornada pelo perfil — nunca construa a URL manualmente.

**No Angular**, para forçar recarregamento após upload (o browser cacheia a URL antiga):
```typescript
// Adicione um query param após o upload para bustar o cache do browser:
this.avatarUrl = response.avatarUrl + '?v=' + Date.now();
```

---

### PUT /users/me/password — Autenticado

```json
// Request
{
  "currentPassword": "string",
  "newPassword": "string",        // deve respeitar PasswordPolicy
  "totpCode": "123456",           // obrigatório se o usuário tiver 2FA ativo (6 dígitos ou backup code)
  "revokeOtherSessions": false    // se true, revoga todos os refresh tokens e bloqueia JWTs anteriores
}
// Response 204
```

**Comportamento de sessão:**
- `revokeOtherSessions: false` (padrão) — apenas a senha é trocada; sessões em outros dispositivos continuam ativas.
- `revokeOtherSessions: true` — todos os refresh tokens são revogados e todos os JWTs emitidos antes deste momento são bloqueados.

**Erros:** `400 INVALID_PASSWORD`, `400 TOTP_CODE_REQUIRED`, `400 INVALID_TOTP_CODE`, `400 VALIDATION_ERROR`

---

### GET /users — Permissão: USER_READ

```
Query params:
  search:  string   (filtra username/email, parcial, case-insensitive)
  enabled: boolean
  sortBy:  "id" | "username" | "email" | "enabled" | "createdAt"  (default: "id")
  sortDir: "asc" | "desc"                           (default: "asc")
  page:    int  (default: 0)
  size:    int  (default: 20, max: 100)
```

```json
// Response 200
{
  "content": [UserResponse],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

### POST /users — Permissão: USER_CREATE

```json
// Request
{
  "username": "string",   // 3–80, obrigatório
  "password": "string",   // PasswordPolicy, obrigatório
  "email": "string",      // email válido, max 254, opcional
  "roles": ["ROLE_USER"]  // opcional, padrão []
}
// Response 201 + header Location: /users/{id}
```

**Erros:** `409 USERNAME_ALREADY_EXISTS`, `400 VALIDATION_ERROR`

---

### GET /users/{id} — Permissão: USER_READ

```
// Response 200 → UserResponse / 404 USER_NOT_FOUND
```

---

### PATCH /users/{id} — Permissão: USER_UPDATE

```json
// Request
{
  "username": "string",  // 3–80, obrigatório
  "email": "string"      // email válido, min 1 char, max 254, opcional (null = não altera)
}
// Response 200 → UserResponse / 404 / 409
```

> `currentPassword` é ignorado nesta rota (admin não precisa de confirmação de senha).

**Fluxo de troca de email (admin):** idêntico ao `PATCH /users/me` — a conta **não** é desabilitada, `pendingEmail` é definido e um código é enviado ao novo endereço. O usuário confirma normalmente via `POST /auth/confirm-email-change`. O evento auditado é `EMAIL_CHANGE_REQUESTED`.

---

### PUT /users/{id}/enable — Permissão: USER_STATUS

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### PUT /users/{id}/disable — Permissão: USER_STATUS

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### DELETE /users/{id} — Permissão: USER_DELETE

**Soft delete** — o registro é marcado como deletado (`deleted_at`) mas não removido do banco.  
Audit logs do usuário são preservados. O username e email ficam liberados para reuso.

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### POST /users/{username}/roles/{roleName} — Permissão: USER_ROLE_ASSIGN

```
// Response 204 / 404 USER_NOT_FOUND
```

---

### DELETE /users/{username}/roles/{roleName} — Permissão: USER_ROLE_ASSIGN

```
// Response 204 / 404
```

---

## Roles — `/roles`

### GET /roles — Permissão: ROLE_READ

```
Query: search (string, opcional), page, size
```

```json
// Response 200
{
  "content": [RoleResponse],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1
}
```

---

### GET /roles/{name} — Permissão: ROLE_READ

```json
// Response 200 → RoleResponse / 404 ROLE_NOT_FOUND
```

---

### POST /roles — Permissão: DEV_ROLE_MANAGE

> Exige token **DEV-elevado** (`POST /auth/dev/complete`). `ROLE_ADMIN` não tem essa permissão — apenas `ROLE_DEV` pós-elevação.

```json
{ "name": "ROLE_ANALYST" }   // 3–80 chars, prefixo ROLE_ por convenção
// Response 201 + Location / 409 ROLE_ALREADY_EXISTS
```

---

### DELETE /roles/{name} — Permissão: DEV_ROLE_MANAGE

> Exige token **DEV-elevado**.

```
// Response 204 / 404 ROLE_NOT_FOUND
```

---

### POST /roles/{roleName}/permissions/{permissionName} — Permissão: ROLE_MANAGE_PERMISSIONS

```
// Response 204 / 404
```

> **Guard DEV_ELEVATED:** atribuir `DEV_ROLE_MANAGE` ou `DEV_PERMISSION_MANAGE` a qualquer role exige, além da permissão `ROLE_MANAGE_PERMISSIONS`, que o token carregue a authority `DEV_ELEVATED` (obtida via `POST /auth/dev/complete`). Tentar sem elevação resulta em `403 ACCESS_DENIED`.

---

### DELETE /roles/{roleName}/permissions/{permissionName} — Permissão: ROLE_MANAGE_PERMISSIONS

```
// Response 204 / 404
```

> **Guard DEV_ELEVATED:** remover `DEV_ROLE_MANAGE` ou `DEV_PERMISSION_MANAGE` de qualquer role exige, além da permissão `ROLE_MANAGE_PERMISSIONS`, que o token carregue a authority `DEV_ELEVATED`. O guard é idêntico ao `assignPermission` — as permissões DEV são protegidas em ambas as direções.

---

## Permissions — `/permissions`

### GET /permissions — Permissão: PERMISSION_READ

```
Query: page, size
// Response 200 → PageResult<PermissionResponse>
```

---

### GET /permissions/{name} — Permissão: PERMISSION_READ

```json
// Response 200 → PermissionResponse / 404 PERMISSION_NOT_FOUND
```

---

### POST /permissions — Permissão: DEV_PERMISSION_MANAGE

> Exige token **DEV-elevado** (`POST /auth/dev/complete`). `ROLE_ADMIN` não tem essa permissão.

```json
{ "name": "REPORTS_READ" }   // 3–80 chars
// Response 201 + Location / 409 PERMISSION_ALREADY_EXISTS
```

---

### DELETE /permissions/{name} — Permissão: DEV_PERMISSION_MANAGE

> Exige token **DEV-elevado**.

```
// Response 204 / 404 PERMISSION_NOT_FOUND
```

---

## Audit Logs — `/audit-logs`

### GET /audit-logs — Permissão: AUDIT_READ

```
Query params:
  username: string  (opcional)
  action:   string  (opcional — ver /audit-logs/actions para valores válidos)
  from:     ISO-8601 datetime (ex: 2026-05-01T00:00:00Z)
  to:       ISO-8601 datetime (ex: 2026-05-31T23:59:59Z)
  page:     int (default: 0)
  size:     int (default: 20, max: 100)
```

```json
// Response 200
{
  "content": [
    {
      "id": 1,
      "who": "admin",
      "action": "USER_LOGGED_IN",
      "target": null,           // "user:joao", "role:ROLE_ADMIN", "permission:USER_READ"
      "details": null,          // JSON string com detalhes extras (pode ser null)
      "ipAddress": "192.168.1.1",
      "timestamp": "2026-05-30T16:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 500, "totalPages": 25
}
```

---

### GET /audit-logs/actions — Permissão: AUDIT_READ

Retorna todos os tipos de evento válidos para uso no filtro `?action=`.

```json
// Response 200
["ACCOUNT_LOCKED", "EMAIL_CHANGE_CONFIRMED", "LOGIN_FAILED", ...]
```

**Todos os EventType disponíveis:**

| Grupo | Eventos |
|-------|---------|
| Auth | `USER_LOGGED_IN`, `USER_LOGGED_OUT`, `USER_SESSIONS_CLEARED`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`, `TOKEN_THEFT_DETECTED` |
| Lifecycle | `USER_REGISTERED`, `USER_EMAIL_VERIFIED`, `USER_CREATED`, `USER_DELETED`, `USER_UPDATED`, `USER_EMAIL_CHANGED`, `USER_ROLE_ASSIGNED`, `USER_ROLE_REMOVED`, `USER_ENABLED`, `USER_DISABLED`, `USER_PASSWORD_CHANGED` |
| Password | `PASSWORD_RESET_REQUESTED`, `PASSWORD_RESET_COMPLETED` |
| Email | `EMAIL_CHANGE_REQUESTED`, `EMAIL_CHANGE_CONFIRMED` |
| RBAC | `ROLE_CREATED`, `ROLE_DELETED`, `PERMISSION_CREATED`, `PERMISSION_DELETED`, `PERMISSION_ASSIGNED_TO_ROLE`, `PERMISSION_REMOVED_FROM_ROLE` |
| 2FA | `TOTP_ENABLED`, `TOTP_DISABLED`, `TOTP_BACKUP_CODES_REGENERATED`, `TOTP_REPLACED` |
| DEV | `DEV_ELEVATION_COMPLETED` |
| OAuth | `OAUTH_GOOGLE_LOGIN` |
| Segurança | `ACCESS_DENIED` |

---

## Notificações — `/notifications`

Todas as rotas exigem autenticação Bearer. Operações são sempre escopadas ao usuário autenticado — não é possível ler ou marcar notificações de outro usuário.

### GET /notifications — Autenticado

Lista as notificações do usuário. Suporta filtro por não-lidas e paginação.

| Parâmetro | Tipo | Default | Descrição |
|-----------|------|---------|-----------|
| `unreadOnly` | boolean | `false` | Se `true`, retorna apenas notificações não lidas |
| `page` | int | `0` | Número da página (começa em 0) |
| `size` | int | `20` | Tamanho da página (máx. 100) |

```json
// Response 200
{
  "content": [
    {
      "id": 1,
      "type": "PASSWORD_CHANGED",
      "title": "Senha alterada",
      "body": "Sua senha foi alterada. Se não foi você, contate o suporte.",
      "read": false,
      "readAt": null,
      "createdAt": "2026-06-09T03:04:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

### GET /notifications/unread-count — Autenticado

Retorna o total de notificações não lidas do usuário autenticado.

```json
// Response 200
{ "count": 3 }
```

---

### PATCH /notifications/{id}/read — Autenticado

Marca uma notificação específica como lida. Silencioso se a notificação não pertencer ao usuário autenticado (não retorna 403 — por design, para não vazar IDs).

```
// Response 204 No Content
```

---

### PATCH /notifications/read-all — Autenticado

Marca todas as notificações do usuário autenticado como lidas.

```
// Response 204 No Content
```

---

### DELETE /notifications/{id} — Autenticado

Remove permanentemente uma notificação do usuário autenticado. Silencioso se a notificação não pertencer ao usuário (não retorna 403 — por design, para não vazar IDs).

```
// Response 204 No Content
```

---

### GET /notifications/stream — Autenticado

Abre uma conexão SSE (Server-Sent Events) para receber notificações em tempo real. Cada notificação persistida é enviada como evento `notification` no stream.

| Detalhe | Valor |
|---------|-------|
| Content-Type | `text/event-stream` |
| Timeout | 30 minutos |
| Nome do evento SSE | `notification` |

```
// Exemplo de evento recebido
event: notification
data: {"id":42,"type":"PASSWORD_CHANGED","title":"Senha alterada","body":"...","read":false,"readAt":null,"createdAt":"2026-06-09T03:04:00Z"}
```

---

### GET /notifications/preferences — Autenticado

Retorna as preferências de notificação do usuário para todos os `NotificationType`. Tipos sem preferência explícita retornam com `inAppEnabled: true, emailEnabled: true` (padrão).

```json
// Response 200
[
  { "type": "PASSWORD_CHANGED", "inAppEnabled": true, "emailEnabled": true },
  { "type": "ACCOUNT_LOCKED",   "inAppEnabled": true, "emailEnabled": false },
  ...
]
```

---

### PUT /notifications/preferences/{type} — Autenticado

Atualiza a preferência de notificação para um tipo específico. O path `{type}` deve ser um valor válido de `NotificationType`.

```json
// Request body
{ "inAppEnabled": false, "emailEnabled": true }

// Response 200
{ "type": "PASSWORD_CHANGED", "inAppEnabled": false, "emailEnabled": true }
```

| Status | Condição |
|--------|----------|
| 200 | Preferência atualizada com sucesso |
| 400 `INVALID_ENUM_VALUE` | `{type}` inválido — não é um `NotificationType` reconhecido; a mensagem de erro lista os valores aceitos |
| 401 | Sem autenticação |
| 429 | Rate limit atingido — header `Retry-After: <seg>` |

---

### Tipos de notificação (`NotificationType`)

| Tipo | Evento que dispara | Email padrão enviado? |
|------|-------------------|-----------------------|
| `PASSWORD_CHANGED` | `USER_PASSWORD_CHANGED` | ✅ `sendPasswordChangedAlert` |
| `ACCOUNT_LOCKED` | `ACCOUNT_LOCKED` | ✅ `sendAccountLockedAlert` |
| `TOTP_ENABLED` | `TOTP_ENABLED` | ✅ `sendTotpStatusAlert(enabled=true)` |
| `TOTP_DISABLED` | `TOTP_DISABLED` | ✅ `sendTotpStatusAlert(enabled=false)` |
| `TOKEN_THEFT_DETECTED` | `TOKEN_THEFT_DETECTED` | ✅ `sendTokenTheftAlert` |
| `EMAIL_CHANGED` | `USER_EMAIL_CHANGED` | ❌ |
| `ROLE_ASSIGNED` | `USER_ROLE_ASSIGNED` | ❌ |
| `ROLE_REMOVED` | `USER_ROLE_REMOVED` | ❌ |
| `ACCOUNT_DISABLED` | `USER_DISABLED` | ❌ |
| `SYSTEM` | — (uso programático futuro) | ❌ |

> **Preferências:** o comportamento de cada coluna ("in-app" e "email") pode ser sobrescrito individualmente via `PUT /notifications/preferences/{type}`. O `NotificationEventListener` verifica as preferências antes de persistir ou enviar email.

---

## Stats — `/stats`

### GET /stats — Permissões: USER_READ **e** ROLE_READ

```json
// Response 200
{
  "totalUsers": 100,
  "activeUsers": 95,
  "disabledUsers": 5,
  "totalRoles": 3,
  "totalPermissions": 25
}
```

---

## System Config — `/system/config`

Gerenciamento de feature flags em runtime. Apenas flags da whitelist `PUBLIC_KEYS` podem ser alteradas via API (`auth.google.enabled`, `auth.google.register.enabled`, `auth.registration.enabled`, `auth.forgot-password.enabled`). Flags de sistema como `security.maintenance.enabled` e `security.2fa.required` só podem ser alteradas diretamente no banco.

### GET /system/config/public — Público

Retorna as feature flags públicas (sem autenticação). Inclui apenas as chaves da whitelist que existem no banco.

```json
// Response 200
{
  "auth.google.enabled": "true",
  "auth.google.register.enabled": "true",
  "auth.registration.enabled": "true",
  "auth.forgot-password.enabled": "true"
}
```

### GET /system/config — Autoridade: DEV_ELEVATED

Retorna todas as feature flags do banco.

```json
// Response 200
{
  "auth.google.enabled": "true",
  "auth.registration.enabled": "true",
  "security.maintenance.enabled": "false",
  "security.2fa.required": "false",
  "module.audit-logs.enabled": "true",
  "module.roles.enabled": "true"
}
```

**Erros:** `401` sem autenticação, `403` sem `DEV_ELEVATED`.

### PUT /system/config/{key} — Autoridade: DEV_ELEVATED

Atualiza uma flag da whitelist pública.

```json
// Request body
{ "value": "false" }
```

| Campo | Tipo | Validação |
|-------|------|-----------|
| `value` | string | `@NotNull`, máximo 255 caracteres |

**Responses:**
- `204 No Content` — atualizado com sucesso (evicta cache imediatamente)
- `400 INVALID_ARGUMENT` — chave não está na whitelist ou body inválido
- `401` — sem autenticação
- `403` — sem `DEV_ELEVATED`

---

## System Info — `/system/info`

### GET /system/info — Autoridade: DEV_ELEVATED

Retorna informações do ambiente ativo. Útil para diagnosticar qual perfil está rodando.

```json
// Response 200
{
  "status": "UP",
  "profile": "dev",
  "profiles": ["dev"]
}
```

**Erros:** `401` sem autenticação, `403` sem `DEV_ELEVATED`.

---

## Tipos TypeScript

```typescript
// ---- Tokens ----

interface TokenPairResponse {
  accessToken: string;
  refreshToken: string;      // também enviado como cookie HttpOnly
  tokenType: 'Bearer';
  expiresIn: number;         // segundos — 900 (15 min) em dev
}

interface TwoFactorChallengeResponse {
  status: 'PENDING_2FA';
  challengeToken: string;
  expiresInSeconds: number;  // 300 (5 min)
}

type LoginResponse = TokenPairResponse | TwoFactorChallengeResponse;

// Discriminador:
function isPending2FA(r: LoginResponse): r is TwoFactorChallengeResponse {
  return (r as TwoFactorChallengeResponse).status === 'PENDING_2FA';
}


// ---- User ----

// Retornado por GET /users, GET /users/{id}, POST /users, PATCH /users/{id}
interface UserResponse {
  id: number;
  username: string;
  enabled: boolean;
  email: string | null;
  emailVerified: boolean;
  avatarUrl: string | null;  // URL pública do avatar ou null se sem avatar
  createdAt: string;         // ISO-8601
  roles: string[];           // ex: ["ROLE_ADMIN"]
  permissions: string[];     // ex: ["USER_READ", "ROLE_READ"]
}

// Retornado por GET /users/me e PATCH /users/me
// Adiciona pendingEmail ao UserResponse (troca de email em andamento)
interface UserProfileResponse extends UserResponse {
  pendingEmail: string | null;  // não-nulo = código enviado ao novo endereço, aguardando confirmação
}

// Retornado por POST /users/me/avatar
interface AvatarUploadResponse {
  avatarUrl: string;  // nova URL — use como novo valor de UserProfileResponse.avatarUrl
}


// ---- RBAC ----

interface RoleResponse {
  id: number;
  name: string;          // sempre prefixo ROLE_
  permissions: string[];
}

interface PermissionResponse {
  id: number;
  name: string;
}


// ---- Paginação ----

interface PageResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}


// ---- Session ----

interface SessionInfo {
  id: number;
  createdAt: string;    // ISO-8601
  expiresAt: string;
  ipAddress: string | null;
  userAgent: string | null;
}


// ---- Audit ----

interface AuditLogEntry {
  id: number;
  who: string;
  action: string;           // EventType
  target: string | null;    // "user:x", "role:y", "permission:z"
  details: string | null;   // JSON string — parsear se precisar
  ipAddress: string | null;
  timestamp: string;        // ISO-8601
}


// ---- Stats ----

interface StatsResponse {
  totalUsers: number;
  activeUsers: number;
  disabledUsers: number;
  totalRoles: number;
  totalPermissions: number;
}


// ---- Erros ----

interface ApiError {
  message: string;
  errorCode: string;
  timestamp: string;   // ISO-8601
  path: string;
  traceId: string;
}

// 2FA Setup
interface TotpSetupResponse {
  secret: string;
  otpauthUri: string;   // renderizar como QR code
}

interface TotpConfirmResponse {
  backupCodes: string[];  // 8 códigos XXXX-XXXX-XXXX — exibir uma vez
}
```

---

## Permissões disponíveis

| Permissão | Descrição |
|-----------|-----------|
| `USER_CREATE` | Criar conta de usuário |
| `USER_READ` | Listar e visualizar usuários |
| `USER_UPDATE` | Atualizar dados básicos (admin) |
| `USER_DELETE` | Deletar conta |
| `USER_ROLE_ASSIGN` | Atribuir/remover roles |
| `USER_STATUS` | Ativar/desativar conta |
| `ROLE_CREATE` | Criar role |
| `ROLE_READ` | Listar roles |
| `ROLE_DELETE` | Deletar role |
| `ROLE_MANAGE_PERMISSIONS` | Associar/remover permissões de roles |
| `PERMISSION_CREATE` | Criar permissão |
| `PERMISSION_READ` | Listar permissões |
| `PERMISSION_DELETE` | Deletar permissão |
| `AUDIT_READ` | Ver audit logs |

---

## PasswordPolicy

```
Mínimo: 8 caracteres
Máximo: 120 caracteres
Deve conter: 1 maiúscula, 1 minúscula, 1 dígito, 1 especial
Regexp: ^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d]).+$
```

---

## Cookie refreshToken

| Atributo | Valor |
|----------|-------|
| Name | `refreshToken` |
| Path | `/auth` |
| HttpOnly | `true` |
| SameSite | `Strict` |
| Max-Age | 604800 (7 dias) |
| Secure | `true` em hml/prod, `false` em dev |

O browser envia o cookie **automaticamente** apenas em requisições para `/auth/*`.  
No Angular: `withCredentials: true` apenas nas chamadas a `/auth/*` (login, refresh, logout, 2fa/verify).

---

## Configuração CORS (dev)

Backend: `CORS_ALLOWED_ORIGINS=http://localhost:4200`, `CORS_ALLOW_CREDENTIALS=true` e `CORS_ALLOWED_METHODS=GET,POST,PUT,DELETE,OPTIONS,PATCH`.

```typescript
// Angular — interceptor de autenticação
// withCredentials: true é necessário em todos os endpoints que enviam ou recebem o cookie refreshToken
const authPaths = [
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/2fa/verify',
  '/auth/oauth2/google',  // recebe o cookie refreshToken na resposta
];

function needsCredentials(url: string): boolean {
  return authPaths.some(p => url.includes(p));
}
```

---

## Fluxo de autenticação resumido

```
── Login com usuário/senha ──────────────────────────────────────────
1. POST /auth/login
   ├─ status=PENDING_2FA  →  POST /auth/2fa/verify  →  TokenPair
   └─ TokenPair (accessToken + cookie refreshToken)

── Login com Google ─────────────────────────────────────────────────
1. Frontend obtém id_token via Google Identity Services
2. POST /auth/oauth2/google { idToken }
   └─ TokenPair (accessToken + cookie refreshToken)
   (cria conta ou vincula à existente automaticamente)

── Em cada request autenticado ──────────────────────────────────────
   Authorization: Bearer <accessToken>

── Ao receber 401 (access token expirado) ───────────────────────────
   POST /auth/refresh  (cookie enviado automaticamente)
   └─ novo TokenPair  →  repetir request original

── Ao receber REFRESH_TOKEN_EXPIRED ou REFRESH_TOKEN_REUSED ─────────
   Redirecionar para /login

── Logout ────────────────────────────────────────────────────────────
   POST /auth/logout  →  invalida token + limpa cookie
```

---

## Monitoramento — `/actuator`

| Endpoint | Acesso | Descrição |
|----------|--------|-----------|
| `GET /actuator/health` | Público | Status geral |
| `GET /actuator/health/liveness` | Público | Liveness probe (ECS/Kubernetes) |
| `GET /actuator/health/readiness` | Público | Readiness probe (inclui DB + Redis) |
| `GET /actuator/info` | Público | Metadados da aplicação |
| `GET /actuator/prometheus` | `ROLE_ADMIN` | Métricas no formato Prometheus (HML + Prod) |

O endpoint `/actuator/prometheus` pode ser usado por Grafana, Datadog, CloudWatch agent ou qualquer coletor Prometheus-compatível.

---

## Configuração do ambiente HML local

Para rodar o HML localmente com Docker Compose e ter Swagger + cookies funcionando:

```env
# .env (raiz do projeto — não versionar)
SPRING_PROFILES_ACTIVE=hml
DB_PASSWORD=postgres
REDIS_PASSWORD=hml_redis_2026
JWT_SECRET=<gere com: openssl rand -base64 32>
CORS_ALLOWED_ORIGINS=http://localhost:4200
CORS_ALLOW_CREDENTIALS=true
COOKIE_SECURE=false          # cookies funcionam em HTTP local
SWAGGER_ENABLED=true         # habilita Swagger UI em HML
TOTP_ENCRYPTION_KEY=<gere com: openssl rand -base64 32>
AVATAR_BASE_URL=http://localhost:8080/avatars
RESEND_API_KEY=<sua-chave-resend>
RESEND_FROM=noreply@seudominio.com
GOOGLE_CLIENT_ID=<seu-client-id>.apps.googleusercontent.com   # obrigatório para login com Google
```

Iniciar a stack:
```bash
docker compose up -d        # sobe PostgreSQL (5433) + Redis (6380)
./mvnw spring-boot:run      # sobe o Spring Boot em HML
```

Swagger UI disponível em: `http://localhost:8080/swagger-ui.html`

---

## Convenções

- Roles **sempre** com prefixo `ROLE_` (ex: `ROLE_ADMIN`, nunca `ADMIN`)
- Códigos de verificação de email: `[A-Z0-9]{12}` exatamente
- Código TOTP: 6 dígitos numéricos
- Backup codes: formato `XXXX-XXXX-XXXX`, X ∈ `[A-Z0-9]`, 8 códigos por usuário
- Timestamps: ISO-8601 UTC
- Paginação começa em `page=0`
- Rate limiting em endpoints de auth: `429` com header `Retry-After: <segundos>`
- Emails são enviados de forma **assíncrona** em HML/Prod — o HTTP response não espera a entrega
- Soft delete: `DELETE /users/{id}` não remove o registro — apenas marca `deleted_at`
