# API Reference — mahal-commerce

**Base URL (dev):** `http://localhost:8080` 
**Auth:** `Authorization: Bearer <accessToken>` em todos os endpoints, exceto os marcados como **Público**.

> Swagger UI disponível em `http://localhost:8080/swagger-ui.html` — em `dev` e `hml` apenas.
> Em `prod` o springdoc é desabilitado (PLAT-C029), e nem a UI nem `/v3/api-docs/**` são servidos.

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
| `SKU_ALREADY_EXISTS` | 409 | SKU já cadastrado — vale para o SKU pai e para os de variação, que dividem o mesmo espaço de nomes |
| `PRODUCT_NOT_FOUND` | 404 | SKU não existe no catálogo (nem como SKU pai, nem como SKU de variação). Barra movimentação e ponto de reposição para SKU inexistente ou digitado errado |
| `DATA_INTEGRITY_VIOLATION` | 409 | Conflito com um registro já existente que escapou da validação de aplicação — tipicamente uma corrida entre duas requisições simultâneas. Refazer a operação costuma resolver |
| `OAUTH_TOKEN_INVALID` | 401 | Token Google inválido, expirado ou audience incorreto |
| `AVATAR_TOO_LARGE` | 400 | Arquivo de avatar excede 2 MB |
| `INVALID_AVATAR_FORMAT` | 400 | Formato não suportado — aceito JPEG, PNG, WebP |
| `VALIDATION_ERROR` | 400 | Campos inválidos (bean validation) |
| `UNREADABLE_BODY` | 400 | Body ausente ou JSON malformado |
| `MISSING_PARAMETER` | 400 | Parâmetro de query obrigatório ausente — a mensagem nomeia o parâmetro |
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
  "otpauthUri": "otpauth://totp/mahal-commerce:username?secret=...&issuer=mahal-commerce"
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

## Estoque — `/estoque`

### GET /estoque/products — Permissão: ESTOQUE_PRODUCT_READ

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<ProductResponse> / 400 VALIDATION_ERROR
```

---

### POST /estoque/products — Permissão: ESTOQUE_PRODUCT_MANAGE

```json
{
  "sku": "NARG-001",        // 3–50 chars, obrigatório
  "name": "Narguile Aladin", // obrigatório
  "category": "narguile",    // opcional
  "variants": [               // opcional — produto pode não ter variações
    {
      "sku": "NARG-001-M",   // 3–50 chars, obrigatório
      "attributes": [
        { "type": "sabor", "value": "menta" }
      ]
    }
  ]
}
// Response 201 + Location → ProductResponse / 409 SKU_ALREADY_EXISTS / 400 VALIDATION_ERROR
```

```json
// ProductResponse
{
  "id": 1,
  "sku": "NARG-001",
  "name": "Narguile Aladin",
  "category": "narguile",
  "active": true,
  "variants": [
    { "id": 1, "sku": "NARG-001-M", "active": true, "attributes": [{ "type": "sabor", "value": "menta" }] }
  ]
}
```

---

### PATCH /estoque/products/{sku} — Permissão: ESTOQUE_PRODUCT_MANAGE

```json
{
  "name": "Narguilé Aladin 2.0",  // opcional, 1–255 — ausente ou null = manter
  "category": "narguile-premium"   // opcional, até 100 — ausente ou null = manter
}
// Response 200 → ProductResponse / 404 PRODUCT_NOT_FOUND / 400 VALIDATION_ERROR
```

Alteração **parcial**: corpo `{}` é um no-op válido. Não altera `sku` nem as variações — o SKU é
referenciado como texto livre por `stock_balance`, `stock_movement` e `stock_reorder_point`, sem
FK, então renomeá-lo tornaria órfão todo o histórico do produto. Limitação da semântica
"null = manter": não há como **limpar** a `category`, só trocá-la.

---

### PATCH /estoque/products/{sku}/active — Permissão: ESTOQUE_PRODUCT_MANAGE

```json
{ "active": false }   // obrigatório — corpo sem o campo é 400, não um "desativar" implícito
// Response 200 → ProductResponse / 404 PRODUCT_NOT_FOUND / 400 VALIDATION_ERROR
```

Desativar **não apaga**: o SKU continua existindo, com saldo e histórico válidos. O efeito é
sobre a movimentação — produto inativo recusa `ENTRADA` (manual ou por recebimento de Compras)
com `409 PRODUCT_INACTIVE`, mas continua aceitando `SAIDA` e venda no PDV, para escoar o saldo
remanescente, e `AJUSTE`, que é o caminho de correção de inventário.

Desativar um SKU **pai** tira também as variações de circulação: um SKU de variação só conta como
ativo se ele e o produto pai estiverem ativos.

---

### POST /estoque/warehouses — Permissão: ESTOQUE_WAREHOUSE_MANAGE

```json
{
  "code": "LOJA-01",         // 2–50 chars, obrigatório, único
  "name": "Loja Centro",      // obrigatório
  "type": "LOJA_FISICA"       // obrigatório — LOJA_FISICA | ECOMMERCE
}
// Response 201 + Location → WarehouseResponse / 409 WAREHOUSE_CODE_ALREADY_EXISTS / 400 VALIDATION_ERROR
```

```json
// WarehouseResponse
{
  "id": 1,
  "code": "LOJA-01",
  "name": "Loja Centro",
  "type": "LOJA_FISICA",
  "active": true
}
```

---

### PATCH /estoque/warehouses/{code} — Permissão: ESTOQUE_WAREHOUSE_MANAGE

```json
{
  "name": "Loja Centro Reformada",  // opcional — ausente ou null = manter
  "type": "ECOMMERCE"                // opcional — LOJA_FISICA | ECOMMERCE; valor desconhecido é 400
}
// Response 200 → WarehouseResponse / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Não altera o `code`: é a identidade pública do depósito, usada como `warehouseCode` em toda a API.

---

### PATCH /estoque/warehouses/{code}/active — Permissão: ESTOQUE_WAREHOUSE_MANAGE

```json
{ "active": false }   // obrigatório
// Response 200 → WarehouseResponse / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Mesma regra do produto: depósito inativo recusa `ENTRADA` com `409 WAREHOUSE_INACTIVE` e continua
despachando `SAIDA`.

---

### GET /estoque/warehouses — Permissão: ESTOQUE_WAREHOUSE_READ

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<WarehouseResponse>, ordenado por id
// 400 VALIDATION_ERROR (page ou size fora da faixa)
```

⚠️ **Mudança de contrato no EST-C005:** antes devolvia `WarehouseResponse[]` — a lista inteira,
sem paginação. Agora devolve um `PageResult`, então os depósitos estão em `content`.

---

### GET /estoque/stock-balance — Permissão: ESTOQUE_WAREHOUSE_READ

```
Query: sku (obrigatório, 3..50), warehouseCode (obrigatório, 2..50)
// Response 200 → StockBalanceResponse / 404 WAREHOUSE_NOT_FOUND
// 400 VALIDATION_ERROR (sku ou warehouseCode em branco ou fora do tamanho)
// 400 MISSING_PARAMETER (parâmetro ausente)
```

```json
// StockBalanceResponse — quantity é 0 se ainda não houve nenhuma movimentação para o par sku/depósito
{
  "sku": "NARG-001",
  "warehouseCode": "LOJA-01",
  "quantity": 0
}
```

---

### POST /estoque/movements — Permissão: ESTOQUE_STOCK_MANAGE

```json
{
  "sku": "NARG-001",           // 3–50 chars, obrigatório
  "warehouseCode": "LOJA-01",   // obrigatório
  "type": "ENTRADA",            // obrigatório — ENTRADA | SAIDA | AJUSTE
  "quantity": 5.000,             // obrigatório; > 0 em ENTRADA/SAIDA, >= 0 em AJUSTE
  "reason": "Recebimento de fornecedor" // obrigatório, máx. 255 chars
}
// Response 201 + Location → StockBalanceResponse (saldo já atualizado)
// 404 PRODUCT_NOT_FOUND (SKU não existe no catálogo) / 404 WAREHOUSE_NOT_FOUND
// 400 INSUFFICIENT_STOCK (SAIDA deixaria o saldo negativo) / 400 VALIDATION_ERROR
// 409 STOCK_UPDATE_CONFLICT (conflito de concorrência otimista — tente novamente)
// 409 DATA_INTEGRITY_VIOLATION (corrida na primeira movimentação do par — refazer resolve)
```

`username` **não** é enviado no corpo — é sempre o usuário autenticado (JWT), nunca informado
pelo cliente da API.

⚠️ **`quantity` tem significado diferente por tipo** (EST-C009). Em `ENTRADA` e `SAIDA` é o
**delta**: soma e subtrai, respectivamente — a saída é rejeitada com 400 se o resultado ficaria
negativo, e zerar exatamente é permitido. Em `AJUSTE` é o **saldo-alvo**: o saldo passa a valer
exatamente o valor informado, para cima ou para baixo, e zero é um alvo válido (item que acabou).
É o que permite corrigir inventário para baixo sem lançar uma `SAIDA` falsa. Baixar por `AJUSTE`
nunca devolve `INSUFFICIENT_STOCK` — é substituição, não subtração.

Além do lançamento manual, `AJUSTE` é o tipo usado pelo fechamento de um
[balanço de inventário](#balanço-de-inventário--estoquestock-counts--permissão-estoque_stock_manage).

`ENTRADA` é recusada com 409 se o produto ou o depósito estiver **desativado** (EST-F018);
`SAIDA` e `AJUSTE` continuam permitidos.

O `sku` precisa existir no catálogo, como SKU pai ou como SKU de variação; caso contrário a
movimentação é recusada com 404 `PRODUCT_NOT_FOUND` e nada é gravado. Isso vale igualmente para
as movimentações originadas de `POST /pdv/sessions/{id}/sales` e `POST /compras/goods-receipts`,
onde um SKU desconhecido reverte a venda ou o recebimento inteiro.

```json
// StockBalanceResponse (mesmo shape de GET /estoque/stock-balance)
{
  "sku": "NARG-001",
  "warehouseCode": "LOJA-01",
  "quantity": 5.000
}
```

---

### GET /estoque/movements — Permissão: ESTOQUE_STOCK_MANAGE

```
Query: sku (obrigatório, 3..50), warehouseCode (obrigatório, 2..50), page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<StockMovementResponse>
// 404 WAREHOUSE_NOT_FOUND / 400 MISSING_PARAMETER (sku ou warehouseCode ausente)
// 400 VALIDATION_ERROR (parâmetro presente mas fora da faixa)
```

```json
// PageResult<StockMovementResponse> — mais recentes primeiro (created_at DESC, id DESC)
// O desempate por id garante paginação estável: movimentos de uma mesma venda compartilham
// created_at, e sem ele a mesma linha poderia repetir entre páginas.
{
  "content": [
    {
      "id": 9,
      "sku": "NARG-001",
      "warehouseCode": "LOJA-01",
      "type": "SAIDA",
      "quantity": 2.000,
      "reason": "Venda balcão sessão #7",
      "username": "gerente",
      "createdAt": "2026-07-26T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Histórico auditável do par SKU/depósito, incluindo as movimentações geradas automaticamente por
`compras` (recebimento) e `vendas-balcao` (venda) — o `reason` identifica a origem. Par sem
nenhuma movimentação devolve `content` vazio com `200`, não `404`; o `404` é reservado ao
depósito inexistente. Exige `ESTOQUE_STOCK_MANAGE` (e não `ESTOQUE_WAREHOUSE_READ`) porque o
ledger expõe **qual usuário** realizou cada movimentação.

---

### PUT /estoque/products/{sku}/reorder-point — Permissão: ESTOQUE_STOCK_MANAGE

```json
{
  "warehouseCode": "LOJA-01",  // obrigatório
  "minQuantity": 10.000         // obrigatório, >= 0
}
// Response 204 No Content (cria ou atualiza — upsert por (sku, warehouseCode))
// 404 PRODUCT_NOT_FOUND / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Define o ponto de reposição do par SKU/depósito. A partir daí, **toda** movimentação que reduza
o saldo abaixo de `minQuantity` — manual, recebimento de compras ou venda de PDV — notifica
todos os usuários com `ESTOQUE_STOCK_MANAGE`. Sem ponto de reposição cadastrado, nenhuma
notificação é disparada. Não há endpoint para ler ou remover um ponto de reposição.

A notificação é enviada **depois** do commit da operação e **agregada por operação**: uma venda
que derruba vários SKUs abaixo do mínimo gera um único aviso listando todos eles, não um por SKU.
Operação revertida não notifica ninguém.

---

### PUT /estoque/products/{sku}/kit — Permissão: ESTOQUE_KIT_MANAGE

```json
{
  "components": [
    { "componentSku": "CARV-001", "quantity": 2 },
    { "componentSku": "ESS-001", "quantity": 1 }
  ]
}
// Response 200 → ProductResponse (type passa a "KIT")
// 404 PRODUCT_NOT_FOUND (kitSku ou algum componentSku fora do catálogo)
// 409 KIT_RECIPE_EMPTY / KIT_HAS_VARIANTS / KIT_COMPONENT_ALREADY_IN_USE /
//     KIT_SELF_REFERENCE / DUPLICATE_KIT_COMPONENT / KIT_COMPONENT_NOT_SIMPLE
// 400 VALIDATION_ERROR
```

Define (substitui integralmente) a receita de um kit virtual (EST-F015, um nível só — §2.10 do
plano) e promove o produto a `KIT` como efeito colateral. **PUT idempotente:** chamar de novo com
um conjunto diferente de componentes substitui a receita inteira, nunca mescla. Componente
precisa ser `SIMPLES` — kit dentro de kit é proibido por construção — e o próprio SKU do kit não
pode já ser componente de outro kit nem ter variações cadastradas.

Kit nunca tem linha própria em `stock_balance`: `GET /estoque/stock-balance` para um SKU `KIT`
devolve o saldo **derivado**, `min(floor(disponível_componente / quantidade_receita))` sobre os
componentes. `GET /estoque/products/{sku}/price` devolve o custo **derivado**, soma de
`costPrice * quantity` dos componentes — o `salePrice` continua sendo o do próprio kit. Uma
venda do kit (`POST /pdv/sessions/{id}/sales`) e o reembolso correspondente
(`POST /orders/{id}/refund`) explodem transparentemente em uma movimentação de estoque por
componente, com o SKU do kit anexado ao `reason` (ex.: `"Venda balcão sessão #42 (kit
KIT-001)"`) — nenhuma mudança de contrato nesses dois endpoints.

### GET /estoque/products/{sku}/kit — Permissão: ESTOQUE_PRODUCT_READ

```json
// Response 200 → List<KitComponentResponse> (vazio se o SKU nunca foi promovido a kit)
[
  { "componentSku": "CARV-001", "quantity": 2 },
  { "componentSku": "ESS-001", "quantity": 1 }
]
// 404 PRODUCT_NOT_FOUND
```

---

### Balanço de inventário — `/estoque/stock-counts` — Permissão: ESTOQUE_STOCK_MANAGE

O balanço (EST-F006) é uma **sessão**: abre-se para um depósito, contam-se os SKUs aos poucos, e o
fechamento aplica os ajustes de uma vez. Só pode haver **um balanço aberto por depósito** — dois
simultâneos contariam o mesmo saldo e se sobrescreveriam.

```
POST   /estoque/stock-counts            { "warehouseCode": "LOJA-01" }
POST   /estoque/stock-counts/{id}/items { "sku": "NARG-001", "countedQuantity": 37.000 }
POST   /estoque/stock-counts/{id}/close
POST   /estoque/stock-counts/{id}/cancel
GET    /estoque/stock-counts/{id}
GET    /estoque/stock-counts?warehouseCode=LOJA-01&page=0&size=20
```

```json
// StockCountResponse — após o fechamento
{
  "id": 50,
  "warehouseCode": "LOJA-01",
  "status": "FECHADA",            // ABERTA | FECHADA | CANCELADA
  "username": "gerente",
  "createdAt": "2026-07-27T09:00:00Z",
  "closedAt": "2026-07-27T18:00:00Z",
  "items": [
    {
      "id": 1,
      "sku": "NARG-001",
      "countedQuantity": 8.000,   // o que se contou na prateleira
      "expectedQuantity": 10.000, // saldo do sistema no fechamento — null enquanto ABERTA
      "difference": -2.000        // negativo é falta, positivo é sobra
    }
  ]
}
```

**Registrar item** é upsert por SKU: recontar sobrescreve o valor anterior em vez de criar uma
segunda linha. `countedQuantity` aceita **zero** — é o item que acabou ou sumiu, e é justamente o
que o balanço precisa registrar. SKU fora do catálogo é `404 PRODUCT_NOT_FOUND` na hora, não no
fechamento.

**Fechar** grava um `AJUSTE` (saldo-alvo, ver EST-C009) para cada item cuja contagem **divirja** do
saldo do sistema, levando o saldo ao valor contado. Item que bateu não gera movimentação — contagem
certa não polui o ledger. Tudo na mesma transação: se um SKU falhar, nenhum ajuste é aplicado e o
balanço continua aberto. Os alertas de ponto de reposição disparados pelos ajustes saem agregados
depois do commit. Fechar duas vezes é `409 STOCK_COUNT_NOT_OPEN`, não ajuste em dobro.

**Cancelar** abandona o balanço sem tocar em saldo nenhum e libera o depósito para um novo.

Erros: `404 STOCK_COUNT_NOT_FOUND`, `404 WAREHOUSE_NOT_FOUND`, `404 PRODUCT_NOT_FOUND`,
`409 STOCK_COUNT_ALREADY_OPEN`, `409 STOCK_COUNT_NOT_OPEN`.

---

### GET /estoque/integrity/orphan-skus — Permissão: ESTOQUE_STOCK_MANAGE

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<OrphanSkuResponse> / 400 VALIDATION_ERROR
```

```json
// PageResult<OrphanSkuResponse> — uma linha por par SKU/depósito, ordenado por (sku, warehouseCode)
{
  "content": [
    {
      "sku": "NARG-DIGITADO-ERRADO",
      "warehouseCode": "LOJA-01",
      "quantity": 0.000,
      "movementCount": 4,
      "hasReorderPoint": false,
      "lastMovementAt": "2026-02-14T09:12:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Diagnóstico de integridade (EST-C011): lista os pares SKU/depósito que têm saldo, movimentações
ou ponto de reposição gravados mas cujo `sku` **não existe no catálogo** — nem como SKU pai nem
como SKU de variação. As três tabelas de estoque guardam `sku` como texto livre, sem FK para
`product`, então até EST-C002 um SKU digitado errado criava esses registros em silêncio.

**Somente leitura, e não há endpoint de expurgo.** O destino de cada órfão — cadastrar o produto
que faltava (`POST /estoque/products`, e o SKU deixa de ser órfão) ou apagar as linhas — é
decisão humana, porque a consulta não distingue os dois casos e apagar em massa destruiria
histórico legítimo. Para o caminho DBA há o script
[`scripts/estoque-orphan-skus.sql`](../scripts/estoque-orphan-skus.sql), com o bloco de `DELETE`
comentado e a lista de SKUs a preencher à mão.

`quantity` é zero quando o órfão só tem movimentações ou só ponto de reposição, e
`lastMovementAt` é `null` quando o par nunca foi movimentado. Base íntegra devolve `content`
vazio com `200`. Exige `ESTOQUE_STOCK_MANAGE` pela mesma régua do ledger: `ESTOQUE_WAREHOUSE_READ`
não basta.

---

### GET /estoque/reservations — Permissão: ESTOQUE_RESERVATION_READ

```
Query: sku (opcional), warehouseCode (opcional), status (opcional — ACTIVE/CONSUMED/RELEASED/EXPIRED),
       page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<StockReservationResponse> / 404 WAREHOUSE_NOT_FOUND (warehouseCode inexistente)
```

```json
// PageResult<StockReservationResponse>
{
  "content": [
    {
      "id": 10,
      "sku": "NARG-001",
      "warehouseCode": "LOJA-01",
      "quantity": 2.000,
      "ownerReference": "CHECKOUT:abc",
      "status": "ACTIVE",
      "expiresAt": "2026-07-29T12:30:00Z",
      "createdAt": "2026-07-29T12:00:00Z",
      "resolvedAt": null,
      "username": "cliente@exemplo.com"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Listagem de reservas de estoque (EST-F013/EST-F021). `sku`, `warehouseCode` e `status` são
filtros opcionais e se combinam. **Somente leitura**: criar, consumir e liberar reserva é
orquestração interna (checkout do marketplace na Fatia 9, e a liquidação de pedido online no PDV
via `POST /pdv/sessions/{id}/orders/{orderId}/settle`), não uma operação disparada por um
operador via HTTP — daí não haver `POST`/`{id}/release` neste controller.

### GET /estoque/reservations/{id} — Permissão: ESTOQUE_RESERVATION_READ

Consulta uma reserva por id. `404 RESERVATION_NOT_FOUND` se não existir.

---

### GET /estoque/integrity/reservation-mismatch — Permissão: ESTOQUE_STOCK_MANAGE

```
Query: page (default 0, >= 0), size (default 20, 1..100)
// Response 200 → PageResult<ReservationIntegrityMismatchResponse>
```

```json
{
  "content": [
    {
      "sku": "NARG-001",
      "warehouseCode": "LOJA-01",
      "reservedQuantity": 5.000,
      "activeReservationsTotal": 3.000,
      "difference": 2.000
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Diagnóstico de integridade (EST-C013): pares SKU/depósito cujo `stock_balance.reserved_quantity`
diverge da soma das reservas `ACTIVE` no ledger `stock_reservation` para o mesmo par. Diferente do
órfão de SKU, essa divergência não aparece em nenhuma tela — o saldo físico bate normalmente, só
o disponível é que mente. O sintoma é **estoque travado invisível**: a venda recusa por reserva e
nenhuma reserva ativa a explica (ou o inverso), mais difícil de diagnosticar que o overselling que
a reserva existe para evitar. `difference` positivo é contador acima do ledger; negativo é ledger
acima do contador. Somente leitura — a correção de cada linha é decisão humana, no mesmo espírito
de `GET /estoque/integrity/orphan-skus`. Base íntegra devolve `content` vazio com `200`.

---

## Compras — `/compras`

### GET /compras/suppliers — Permissão: COMPRAS_READ

Lista fornecedores paginados (`page` ≥ 0, `size` entre 1 e 100 — default 0/20). Retorna
`PageResult<Supplier>`. **Não há endpoint de criação de fornecedor** — a inserção é feita via SQL
ou repositório.

### POST /compras/goods-receipts — Permissão: COMPRAS_RECEIPT_MANAGE

```json
{
  "supplierId": 1,               // obrigatório
  "warehouseCode": "LOJA-01",    // obrigatório
  "items": [                      // obrigatório, não vazio
    { "sku": "NARG-001", "quantity": 12.000 }  // quantity > 0
  ]
}
// Response 201 → GoodsReceiptResponseDTO
// 404 SUPPLIER_NOT_FOUND / WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

Registra o recebimento e **dá entrada automática no estoque na mesma transação**: cada item gera
um `StockMovement` de `ENTRADA` via `EstoqueUseCase.adjustStock`, atualizando o `StockBalance`.
`username` vem do JWT, nunca do corpo.

```json
// GoodsReceiptResponseDTO
{
  "id": 1,
  "supplierId": 1,
  "warehouseCode": "LOJA-01",
  "username": "admin",
  "receivedAt": "2026-07-23T14:02:11Z",
  "items": [ { "sku": "NARG-001", "quantity": 12.000 } ]
}
```

---

## PDV (Vendas Balcão) — `/pdv`

### GET /pdv/sessions — Permissão: PDV_READ

Lista sessões de caixa paginadas (`page` ≥ 0, `size` entre 1 e 100 — default 0/20). Retorna
`PageResult<CashRegisterSessionResponseDTO>` (era o record de domínio até PDV-C002).

### POST /pdv/sessions — Permissão: PDV_SESSION_MANAGE

```json
{ "openingAmount": 200.00, "warehouseCode": "LOJA-01" }
// 201 + Location → CashRegisterSessionResponseDTO
// 409 SESSION_ALREADY_OPEN / 404 WAREHOUSE_NOT_FOUND / 400 VALIDATION_ERROR
```

**Uma sessão aberta por operador**, garantida por índice parcial único no schema além da checagem
de domínio. O `warehouseCode` informado aqui é o depósito de **todas** as vendas deste caixa — ele
não vai mais no corpo da venda (PDV-C004).

### GET /pdv/sessions/current — Permissão: PDV_READ

Caixa aberto do operador autenticado. `404 NO_OPEN_SESSION`.

### GET /pdv/sessions/{id} — Permissão: PDV_READ

`404 CASH_REGISTER_SESSION_NOT_FOUND`.

### POST /pdv/sessions/{id}/movements — Permissão: PDV_SESSION_MANAGE

```json
{ "type": "SANGRIA", "amount": 150.00, "reason": "depósito no cofre" }
// 201 → CashMovementResponseDTO
// 403 SESSION_NOT_OWNED / 409 CASH_REGISTER_SESSION_CLOSED / 400 VALIDATION_ERROR
```

`type ∈ {SANGRIA, SUPRIMENTO}`. `amount` é **sempre positivo** — o sentido vem do `type`, e a
resposta traz `signedAmount` com o efeito no caixa. `reason` é obrigatório: sangria sem motivo
registrado é indistinguível de desvio.

Exige sessão aberta **e do próprio operador**.

### GET /pdv/sessions/{id}/movements — Permissão: PDV_READ

Lista os movimentos da sessão, na ordem em que aconteceram.

### POST /pdv/sessions/{id}/close — Permissão: PDV_SESSION_CLOSE

```json
{ "countedAmount": 495.00 }
// 200 → CashRegisterSessionResponseDTO com expectedAmount, countedAmount, differenceAmount, diverges
// 404 CASH_REGISTER_SESSION_NOT_FOUND / 409 CASH_REGISTER_SESSION_CLOSED
```

`expectedAmount = openingAmount + vendas concluídas − sangrias + suprimentos`.

**Divergência NÃO impede o fechamento** — é registrada, exatamente como no fechamento de um balanço
de inventário. `differenceAmount` negativo significa falta na gaveta, e é um número legítimo.

Fechar **não** exige ser o dono da sessão: a conferência costuma ser do gerente, e é por isso que
`PDV_SESSION_CLOSE` existe separada de `PDV_SESSION_MANAGE`.

> ⚠️ **`expectedAmount` é aproximado nesta fase.** A conferência da gaveta deveria considerar só o
> que entrou em **dinheiro**, mas `order_payment` só existe na Fatia 3 — por ora o esperado soma
> **todas** as vendas concluídas da sessão. Enquanto a loja só receber dinheiro, o número bate; na
> primeira venda no cartão ele vai acusar uma sobra que não existe fisicamente.

### GET /pdv/pending-online-orders — Permissão: PDV_READ

Pedidos `MARKETPLACE` em `AGUARDANDO_PAGAMENTO` — a lista que o caixa consulta quando o cliente
chega à loja para retirar e pagar um pedido montado no app.

### POST /pdv/sessions/{id}/orders/{orderId}/settle — Permissão: PDV_SALE_MANAGE

Liquida no balcão um pedido feito no aplicativo: **consome a reserva** de estoque (não dá baixa
nova, que debitaria duas vezes), vincula o pedido à sessão de caixa e conclui, emitindo o
`orderNumber`.

O `channel` **continua `MARKETPLACE`** — foi o site que gerou a venda, e é assim que ela tem que
aparecer no relatório de conversão. O que muda é o `sessionId`, que passa a apontar para o caixa que
recebeu o dinheiro.

`403 SESSION_NOT_OWNED` / `404` / `409 INVALID_STATUS_TRANSITION` se o pedido não estiver aguardando
pagamento.

### POST /pdv/sessions/{id}/sales — Permissão: PDV_SALE_MANAGE (+ PDV_SALE_DISCOUNT se houver desconto)

> **Contrato alterado em PDV-F004 e PDV-C004.** `unitPrice` e `warehouseCode` **saíram** do request — o depósito vem da sessão de caixa. Além disso, a venda passa a exigir que a sessão pertença ao operador autenticado (`403 SESSION_NOT_OWNED`). Sobre o preço: o preço e o custo são
> resolvidos pelo servidor a partir do catálogo (`Pricing`, V63). Aceitar preço do cliente HTTP
> tornava indistinguíveis erro de digitação, desconto autorizado e fraude.

```json
{
  "customerId": 42,                // opcional — sem cliente, sem cashback
  "items": [                       // obrigatório, não vazio
    { "sku": "NARG-001", "quantity": 2.000, "discountAmount": 4.00 }
  ]
}
// Response 201 → OrderResponseDTO
// 400 INSUFFICIENT_STOCK (saldo insuficiente para algum item) / 400 VALIDATION_ERROR
// 403 desconto > 0 sem PDV_SALE_DISCOUNT
// 404 CASH_REGISTER_SESSION_NOT_FOUND / 404 PRODUCT_NOT_FOUND
// 409 CASH_REGISTER_SESSION_CLOSED / 409 PRODUCT_NOT_PRICED / 409 DISCOUNT_LIMIT_EXCEEDED
```

Registra a venda e **dá baixa automática no estoque na mesma transação**: cada item gera um
`StockMovement` de `SAIDA`. Se qualquer item não tiver saldo, a transação inteira é revertida. A
venda nasce e termina na mesma transação (`CRIADO → CONCLUIDO`), e é na conclusão que o
`orderNumber` é emitido, de sequência própria.

`quantity` > 0; `discountAmount` ≥ 0 e não pode passar do bruto do item. O teto de desconto por
pedido é `pdv.sale.max-discount-percent` (default **10%**).

Produto sem preço no catálogo **recusa a venda** com `409 PRODUCT_NOT_PRICED` — preço zero e preço
desconhecido não são a mesma coisa.

```json
// OrderResponseDTO
{
  "id": 1,
  "orderNumber": "000001000",
  "channel": "BALCAO",
  "status": "CONCLUIDO",
  "customerId": 42,
  "sessionId": 1,
  "warehouseCode": "LOJA-01",
  "grossAmount": 179.80,
  "discountAmount": 4.00,
  "cashbackRedeemed": 0.00,
  "netAmount": 175.80,
  "changeAmount": null,
  "createdAt": "2026-07-28T18:40:02Z",
  "paidAt": "2026-07-28T18:40:02Z",
  "concludedAt": "2026-07-28T18:40:02Z",
  "items": [
    {
      "id": 1, "sku": "NARG-001", "quantity": 2.000,
      "unitPrice": 89.90, "discountAmount": 4.00,
      "grossAmount": 179.80, "netAmount": 175.80,
      "cashbackPercent": null, "cashbackAmount": null
    }
  ]
}
```

Custo e margem **não** são expostos aqui: são dado de gestão, e `PDV_READ` é a permissão mais
distribuída do módulo.

### GET /pdv/sales/{id} — Permissão: PDV_READ

Retorna `OrderResponseDTO`. `404 ORDER_NOT_FOUND`.

### GET /pdv/sessions/{id}/sales — Permissão: PDV_READ

`PageResult<OrderResponseDTO>` dos pedidos da sessão, do mais recente para o mais antigo
(`page` ≥ 0, `size` 1–100). `404 CASH_REGISTER_SESSION_NOT_FOUND`.

---

## Pedidos (visão do administrador) — `/orders`

Atravessa canais: enxerga venda de balcão e pedido de marketplace na mesma superfície. Separada do
`/pdv`, que enxerga a operação de um caixa. **Aqui aparecem custo e margem**, que o DTO do PDV omite
de propósito — `PDV_READ` é a permissão mais distribuída daquele módulo.

Três permissões, porque as consequências são diferentes: ler é inócuo, avançar estágio é expedição, e
cancelar **devolve mercadoria ao estoque**.

### GET /orders — Permissão: ORDER_READ

Filtros, todos opcionais: `channel` (`BALCAO`/`MARKETPLACE`), `status`, `customerId`, `from`, `to`
(ISO-8601, sobre a data de criação), `page`, `size`. Ordenado do mais recente para o mais antigo.

### GET /orders/{id} — Permissão: ORDER_READ

```json
// OrderAdminResponseDTO — além dos campos do PDV:
{
  "marginAmount": 8.00,              // soma da margem dos itens; null se algum item não tem custo
  "allowedTransitions": ["CANCELADO", "SEPARADO"],
  "items": [ { "costPrice": 18.00, "marginAmount": 8.00, "...": "..." } ]
}
// 404 ORDER_NOT_FOUND
```

`marginAmount` é **nulo, não parcial**, quando algum item não tem custo congelado (pedidos anteriores
à V65): somar só os itens conhecidos produziria um número que parece a margem do pedido e não é.

### POST /orders/{id}/status — Permissão: ORDER_FULFILL

```json
{ "status": "SEPARADO" }
// 200 → OrderAdminResponseDTO / 404 / 409 INVALID_STATUS_TRANSITION
```

`SEPARADO → ENVIADO → ENTREGUE`, nesta ordem. Consulte `allowedTransitions` no detalhe do pedido.

### POST /orders/{id}/cancel — Permissão: ORDER_CANCEL

```json
{ "reason": "cliente desistiu na entrega" }
// 200 → OrderAdminResponseDTO / 404 / 409 INVALID_STATUS_TRANSITION (já cancelado)
```

**Devolve a mercadoria ao estoque** na mesma transação: um `StockMovement` de `ENTRADA` por item, com
o `orderNumber` no motivo. Vale inclusive para pedido já entregue — cancelar um pedido entregue *é*
uma devolução, e devolução é entrada de estoque.

`reason` é obrigatório: estorno sem justificativa registrada é indistinguível de erro.

> **Ainda não acontece aqui:** estorno do **pagamento** (depende da Fatia 3, `order_payment`) e
> `REVERSED` no **cashback** (depende da Fatia 4). Marcar como reembolsado, distinto de cancelado, é
> `PDV-F007` no backlog.

---

## CRM — `/crm`

### POST /crm/customers — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "nome": "Maria Silva",           // obrigatório, máx. 255 chars
  "contato": "11999998888",         // opcional (CRM-C005), máx. 30 chars
  "email": "maria@example.com",     // opcional (CRM-C005), formato de email quando informado, máx. 255 chars, único
  "cpf": "12345678900",             // opcional, identificador OFICIAL do cadastro, exatamente 11 chars, único
  "origem": "loja-fisica"           // opcional, máx. 100 chars
}
// Response 201 + Location → CustomerResponse
// 409 CUSTOMER_EMAIL_ALREADY_EXISTS / 409 CUSTOMER_CPF_ALREADY_EXISTS
// 400 VALIDATION_ERROR (nome ausente, ou email/cpf com formato inválido)
// 400 BAD_REQUEST (nenhum dos três identificadores — cpf, email, contato — foi informado)
```

> **CRM-C005:** pelo menos um entre `cpf`, `email` e `contato` é obrigatório — não há mais um
> campo único obrigatório. Cliente sem `cpf` ("cliente leve", achado só por email/contato) é
> válido e pesquisável, mas não é elegível a cashback quando o programa existir (Fatia 4).

```json
// CustomerResponse — estagio e tags são valores reais (Kanban de atendimento e crm/tags-segmentos).
// ltv, cashback e segmento seguem como placeholders (0 / "NOVO") até os domínios de pedidos e
// cashback existirem no backend (ver crm/listagem-clientes-rfm) — não confundir "segmento"
// (RFM auto-calculado) com "estagio" (Kanban movido manualmente) nem com "tags" (livres, F007).
// Cliente recém-criado sempre vem com tags: [] (ainda não associado a nenhuma tag).
{
  "id": 1,
  "nome": "Maria Silva",
  "contato": "11999998888",
  "email": "maria@example.com",
  "cpf": "12345678900",
  "origem": "loja-fisica",
  "cadastradoEm": "2026-07-20T18:00:00Z",
  "estagio": "NOVO_LEAD",
  "ltv": 0,
  "cashback": 0,
  "segmento": "NOVO",
  "tags": []
}
```

---

### GET /crm/customers/{id} — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CustomerResponse (tags reais do cliente) / 404 CUSTOMER_NOT_FOUND
```

---

### GET /crm/customers/lookup — Permissão: CRM_CUSTOMER_LOOKUP

```
Query: cpf (opcional), email (opcional), contato (opcional) — informe pelo menos um
// Response 200 → CustomerResponse
// 404 CUSTOMER_NOT_FOUND / 400 BAD_REQUEST (nenhum critério informado)
```

Busca pontual de balcão (CRM-F002) — o "CPF na nota?": o operador tenta achar o cliente antes de
cadastrar um novo. Permissão própria, separada de `CRM_CUSTOMER_READ`, porque achar **um** cliente
não é a mesma coisa que listar/exportar a base inteira. Quando mais de um critério vem preenchido,
a prioridade é `cpf` (identificador oficial) → `email` → `contato`. Não achando, o fluxo normal é
seguir para `POST /crm/customers` (cadastro rápido) — venda anônima de balcão nunca passa por
aqui, ela simplesmente não informa `customerId`.

---

### GET /crm/customers — Permissão: CRM_CUSTOMER_READ

```
Query: search (opcional — filtra por nome ou contato, case-insensitive), page, size (máx. 100)
// Response 200 → PageResult<CustomerResponse> — tags sempre [] aqui (evita N+1); use
// GET /crm/customers/{id} ou GET /crm/customers/{id}/tags para as tags reais
```

---

### GET /crm/customers/export — Permissão: CRM_CUSTOMER_EXPORT

```
Query: search (opcional — mesmo filtro de GET /crm/customers, mas sem paginação: exporta todos
os registros correspondentes, não só uma página)
// Response 200 → text/csv;charset=UTF-8, Content-Disposition: attachment; filename="clientes.csv"
// Response 429 RATE_LIMIT_EXCEEDED → acima de 5 requisições/hora pelo mesmo usuário
```

```
Colunas (nessa ordem): id,nome,contato,email,cpf,origem,cadastradoEm,estagio
```

Não inclui `ltv`/`cashback`/`segmento` (placeholder) nem `tags` (exigiria query em lote extra — mesma decisão de evitar N+1 da listagem paginada). Arquivo gerado com quebra de linha `\r\n` (RFC 4180), campos com vírgula/aspas/quebra de linha escapados entre aspas duplas, e prefixo BOM UTF-8 (compatibilidade com Excel para acentos). Permissão dedicada (CRM-C002, separada de `CRM_CUSTOMER_READ` desde 2026-08-04) — ler clientes não dá direito a exportar a base inteira. Rate limit (bucket `crm-export`) e `AuditEvent.CUSTOMER_LIST_EXPORTED` já existiam antes disso.

---

### POST /crm/customers/{id}/notes — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "texto": "Cliente prefere contato por WhatsApp"   // obrigatório, máx. 2000 chars
}
// Response 201 + Location → CustomerNoteResponse / 404 CUSTOMER_NOT_FOUND / 400 VALIDATION_ERROR
```

```json
// CustomerNoteResponse — autor é preenchido automaticamente com o username autenticado
{
  "id": 10,
  "customerId": 1,
  "autor": "gerente",
  "texto": "Cliente prefere contato por WhatsApp",
  "criadoEm": "2026-07-20T20:00:00Z"
}
```

---

### GET /crm/customers/{id}/notes — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CustomerNoteResponse[] (mais recentes primeiro) / 404 CUSTOMER_NOT_FOUND
```

---

### GET /crm/customers/{id}/orders — Permissão: CRM_CUSTOMER_READ

```
// Placeholder: sempre retorna [] até o domínio de pedidos existir no backend.
// Response 200 → [] / 404 CUSTOMER_NOT_FOUND
```

---

### GET /crm/customers/{id}/cashback — Permissão: CRM_CUSTOMER_READ

```
// CRM-F003: extrato real do ledger de cashback, até 100 entradas mais recentes.
// Delega a CashbackUseCase.listCustomerEntries — mesma fonte de GET /cashback/customers/{id}/entries.
// Response 200 → CashbackEntryResponse[] (ver seção "Cashback — /cashback") / 404 CUSTOMER_NOT_FOUND
```

---

### PATCH /crm/customers/{id}/estagio — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "estagio": "EM_ATENDIMENTO"   // obrigatório — NOVO_LEAD | EM_ATENDIMENTO | QUALIFICADO | CLIENTE_ATIVO | INATIVO
}
// Response 200 → CustomerResponse (com o estagio já atualizado) / 404 CUSTOMER_NOT_FOUND
// 400 VALIDATION_ERROR (estagio ausente/inválido) / 400 BAD_REQUEST (estagio igual ao atual)
```

Cada transição é registrada com autor (username autenticado, nunca informado no body) e timestamp — ver `GET .../estagio/historico`.

---

### GET /crm/customers/{id}/estagio/historico — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → StageTransitionResponse[] (mais recentes primeiro) / 404 CUSTOMER_NOT_FOUND
```

```json
// StageTransitionResponse
{
  "id": 20,
  "customerId": 1,
  "de": "NOVO_LEAD",
  "para": "EM_ATENDIMENTO",
  "autor": "gerente",
  "transicionadoEm": "2026-07-20T20:30:00Z"
}
```

---

### GET /crm/dashboard/overview — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CrmDashboardResponse
```

```json
// CrmDashboardResponse — ativo = estagio != INATIVO (decisão de escopo, ver crm/dashboard-overview).
// ltvMedio, disparosWhatsappMes e porSegmento são placeholders até os domínios de pedidos/cashback
// e de campanhas existirem. totalClientes, clientesAtivos e porEstagio são dados reais.
{
  "totalClientes": 42,
  "clientesAtivos": 30,
  "ltvMedio": 0,
  "disparosWhatsappMes": 0,
  "porSegmento": { "NOVO": 42 },
  "porEstagio": { "NOVO_LEAD": 20, "EM_ATENDIMENTO": 10, "QUALIFICADO": 5, "CLIENTE_ATIVO": 5, "INATIVO": 2 }
}
```

---

### POST /crm/tags — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "nome": "VIP"   // obrigatório, máx. 50 chars, único
}
// Response 201 + Location → TagResponse / 409 TAG_ALREADY_EXISTS / 400 VALIDATION_ERROR
```

```json
// TagResponse
{ "id": 1, "nome": "VIP" }
```

---

### GET /crm/tags — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → TagSummaryResponse[]
```

```json
// TagSummaryResponse — clientesCount é dado real (contagem de associações)
{ "id": 1, "nome": "VIP", "clientesCount": 3 }
```

---

### DELETE /crm/tags/{id} — Permissão: CRM_CUSTOMER_MANAGE

```
// Remove a tag e todas as suas associações (ON DELETE CASCADE)
// Response 204 / 404 TAG_NOT_FOUND
```

---

### POST /crm/customers/{id}/tags — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "tagId": 1   // obrigatório
}
// Associa a tag ao cliente (idempotente — associar de novo não duplica)
// Response 204 / 404 CUSTOMER_NOT_FOUND ou TAG_NOT_FOUND / 400 VALIDATION_ERROR
```

---

### DELETE /crm/customers/{id}/tags/{tagId} — Permissão: CRM_CUSTOMER_MANAGE

```
// Response 204 / 404 CUSTOMER_NOT_FOUND ou TAG_NOT_FOUND
```

---

### GET /crm/customers/{id}/tags — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → TagResponse[] / 404 CUSTOMER_NOT_FOUND
```

---

### POST /crm/automacoes — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "nome": "Boas-vindas",               // obrigatório, máx. 100 chars
  "gatilho": "MANUAL",                  // obrigatório — MANUAL | ENTRADA_ESTAGIO (só MANUAL dispara nesta versão)
  "segmentoAlvo": "NOVO_LEAD",          // obrigatório — um CustomerStage (Kanban), não o segmento RFM
  "canal": "EMAIL",                     // obrigatório — WHATSAPP | EMAIL | AMBOS
  "template": "Ola {nome}, seu saldo e {saldo}"  // obrigatório, máx. 2000 chars — placeholders não são interpolados nesta versão
}
// Response 201 + Location → CampaignAutomationResponse / 400 VALIDATION_ERROR / 403
```

```json
// CampaignAutomationResponse
{
  "id": 1,
  "nome": "Boas-vindas",
  "gatilho": "MANUAL",
  "segmentoAlvo": "NOVO_LEAD",
  "canal": "EMAIL",
  "template": "Ola {nome}, seu saldo e {saldo}",
  "ativa": true,
  "criadoEm": "2026-07-21T20:00:00Z"
}
```

---

### GET /crm/automacoes — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CampaignAutomationResponse[]
```

---

### PATCH /crm/automacoes/{id}/ativa — Permissão: CRM_CUSTOMER_MANAGE

```json
{
  "ativa": false   // obrigatório
}
// Response 200 → CampaignAutomationResponse / 404 CAMPAIGN_AUTOMATION_NOT_FOUND / 400 VALIDATION_ERROR
```

---

### DELETE /crm/automacoes/{id} — Permissão: CRM_CUSTOMER_MANAGE

```
// Remove a automação e todo o seu log de disparos (ON DELETE CASCADE)
// Response 204 / 404 CAMPAIGN_AUTOMATION_NOT_FOUND
```

---

### POST /crm/automacoes/{id}/disparar — Permissão: CRM_CUSTOMER_MANAGE

```
// Resolve os clientes cujo estagio == segmentoAlvo da automação e cria 1 CampaignLogEntry por
// cliente, status PENDENTE_INTEGRACAO. NÃO envia mensagem real — o canal de envio ainda não
// existe no backend (ver crm/integracao-canal-envio, F008).
// Response 200 → CampaignLogResponse[] (uma entrada por cliente-alvo) / 404 CAMPAIGN_AUTOMATION_NOT_FOUND
```

```json
// CampaignLogResponse — convertidoEm é sempre null nesta versão (depende do domínio de pedidos,
// inexistente — ver crm/listagem-clientes-rfm)
{
  "id": 10,
  "automationId": 1,
  "customerId": 5,
  "status": "PENDENTE_INTEGRACAO",
  "disparadoEm": "2026-07-21T20:05:00Z",
  "convertidoEm": null
}
```

---

### GET /crm/automacoes/{id}/log — Permissão: CRM_CUSTOMER_READ

```
// Response 200 → CampaignLogResponse[] (mais recentes primeiro) / 404 CAMPAIGN_AUTOMATION_NOT_FOUND
```

---

### GET /crm/canais/status — Permissão: CRM_CUSTOMER_READ

```
// Status de conexão dos canais de envio (WhatsApp/E-mail) — substitui o badge fixo
// "API WhatsApp: Conectada" hoje hardcoded no frontend. "conectado" reflete qual adapter de
// e-mail está ativo no profile (email.provider), não é um health-check de rede ao vivo.
// WhatsApp sempre reporta desconectado — não existe integração real no backend.
// Response 200 → ChannelStatusResponse[]
```

```json
[
  {
    "canal": "EMAIL",
    "conectado": true,
    "provedor": "MAILPIT",
    "detalhe": "Conectado ao Mailpit (ambiente de homologação)"
  },
  {
    "canal": "WHATSAPP",
    "conectado": false,
    "provedor": null,
    "detalhe": "Integração de WhatsApp ainda não implementada"
  }
]
```

---

## Cashback — `/cashback`

CRM-F003, esta fatia cobre **ganhar** cashback (taxa por abrangência, ledger, lançamento na
conclusão da venda, expiração) e as consultas de saldo/extrato/margem. Resgate no balcão
(`CASHBACK_REDEEM`) e ajuste manual (`CASHBACK_ADJUST`) ficam para uma fatia seguinte, isolada.

### POST /cashback/rates — Permissão: CASHBACK_RATE_MANAGE

```json
{
  "scope": "CATEGORY",       // obrigatório — GLOBAL | CATEGORY | SKU
  "scopeRef": "narguile",    // obrigatório para CATEGORY/SKU; ausente para GLOBAL
  "percent": 6.5,            // obrigatório, 0–100
  "validFrom": null,         // opcional — omitido vale a partir de agora
  "validTo": null            // opcional — omitido é vigência em aberto
}
// Response 201 → CashbackRateResponse
// 409 CASHBACK_RATE_ALREADY_EXISTS (já existe taxa ativa e em aberto para a mesma abrangência)
// 400 VALIDATION_ERROR
```

```json
// CashbackRateResponse
{
  "id": 1,
  "scope": "GLOBAL",
  "scopeRef": null,
  "percent": 3.0,
  "active": true,
  "validFrom": "2026-07-29T00:00:00Z",
  "validTo": null,
  "createdAt": "2026-07-29T00:00:00Z"
}
```

---

### GET /cashback/rates — Permissão: CASHBACK_READ

```
Query params: page (default 0), size (default 20, máx 100)
// Response 200 → Page<CashbackRateResponse>
```

---

### PATCH /cashback/rates/{id} — Permissão: CASHBACK_RATE_MANAGE

```json
// Campo ausente ou nulo é mantido. Não altera scope/scopeRef.
{
  "percent": 4.0,
  "active": null,
  "validTo": null
}
// Response 200 → CashbackRateResponse / 404 CASHBACK_RATE_NOT_FOUND
```

---

### GET /cashback/rates/resolve?sku= — Permissão: CASHBACK_READ

```
// Cadeia SKU → CATEGORY → GLOBAL — devolve a regra ativa e vigente mais específica.
// Response 200 → CashbackRateResponse (body vazio se nenhuma taxa se aplica) / 404 PRODUCT_NOT_FOUND
```

---

### GET /cashback/margin-impact?maxShare= — Permissão: CASHBACK_READ

```
// Produtos cuja taxa vigente consome mais de maxShare% da margem do item (Pricing.marginPercent()).
// Response 200 → CashbackMarginImpactResponse[] / 400 VALIDATION_ERROR (maxShare ausente/fora de 0–100)
```

```json
// CashbackMarginImpactResponse
{
  "sku": "CARV-001",
  "name": "Carvão",
  "marginPercent": 18.2,
  "cashbackPercent": 3.0,
  "marginShareConsumed": 16.48   // cashbackPercent / marginPercent * 100
}
```

---

### GET /cashback/customers/{id} — Permissão: CASHBACK_READ

```
// Response 200 → CashbackBalanceResponse / 404 CUSTOMER_NOT_FOUND
```

```json
// CashbackBalanceResponse
{
  "available": "0.00",     // SUM(amount) das entradas já liberadas (available_at <= now())
  "pending": "3.00",       // ganhos EARNED ainda em carência
  "expiringSoon": "0.00"   // disponível que vence nos próximos 30 dias
}
```

---

### GET /cashback/customers/{id}/entries — Permissão: CASHBACK_READ

```
Query params: page (default 0), size (default 20, máx 100)
// Extrato paginado, mais recente primeiro.
// Response 200 → Page<CashbackEntryResponse> / 404 CUSTOMER_NOT_FOUND
```

```json
// CashbackEntryResponse
{
  "id": 10,
  "customerId": 42,
  "orderId": 100,
  "orderItemId": 9,
  "type": "EARNED",         // EARNED | REDEEMED | REVERSED | EXPIRED — só EARNED/EXPIRED são escritos nesta fatia
  "amount": "3.00",
  "availableAt": "2026-08-05T12:00:00Z",   // paidAt + carência (default 7 dias)
  "expiresAt": "2027-02-01T12:00:00Z",     // availableAt + expiração (default 180 dias)
  "reversesEntryId": null,
  "createdAt": "2026-07-29T12:00:00Z"
}
```

Ledger **append-only**: nenhuma linha é atualizada nem deletada. Saldo é sempre
`SUM(amount) WHERE available_at <= now()` — cobre `EARNED` (positivo) e os futuros `REDEEMED`/
`REVERSED`/`EXPIRED` (negativos, referenciando a entrada original via `reversesEntryId`).

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
| `ESTOQUE_PRODUCT_READ` | Listar produtos do estoque |
| `ESTOQUE_PRODUCT_MANAGE` | Criar/gerenciar produtos do estoque |
| `ESTOQUE_WAREHOUSE_READ` | Listar depósitos e consultar saldo |
| `ESTOQUE_WAREHOUSE_MANAGE` | Criar/gerenciar depósitos |
| `ESTOQUE_STOCK_MANAGE` | `POST`/`GET /estoque/movements`, `PUT /estoque/products/{sku}/reorder-point`, `GET /estoque/integrity/orphan-skus`, `GET /estoque/integrity/reservation-mismatch` e todo o `/estoque/stock-counts` (balanço de inventário) |
| `ESTOQUE_RESERVATION_READ` | `GET /estoque/reservations` e `GET /estoque/reservations/{id}` |
| `ESTOQUE_KIT_MANAGE` | `PUT /estoque/products/{sku}/kit` — definir a receita de um kit |
| `ESTOQUE_PRODUCT_MANAGE` | `POST /estoque/products`, `PATCH /estoque/products/{sku}` e `.../active` |
| `ESTOQUE_WAREHOUSE_MANAGE` | `POST /estoque/warehouses`, `PATCH /estoque/warehouses/{code}` e `.../active` |
| `CRM_CUSTOMER_READ` | Leituras de `/crm/**` |
| `CRM_CUSTOMER_MANAGE` | Escritas de `/crm/**` |
| `CRM_CUSTOMER_LOOKUP` | `GET /crm/customers/lookup` — busca pontual por cpf/email/contato, separada de `CRM_CUSTOMER_READ` |
| `CRM_CUSTOMER_EXPORT` | `GET /crm/customers/export` — export CSV da base inteira, separada de `CRM_CUSTOMER_READ` |
| `CASHBACK_RATE_MANAGE` | `POST`/`PATCH /cashback/rates` — criar e alterar taxa de cashback |
| `CASHBACK_READ` | Leituras de `/cashback/**` — taxas, saldo, extrato e diagnóstico de margem |
| `COMPRAS_READ` | `GET /compras/suppliers` |
| `COMPRAS_RECEIPT_MANAGE` | `POST /compras/goods-receipts` — recebimento de mercadoria |
| `PDV_READ` | `GET /pdv/sessions` |
| `PDV_SALE_MANAGE` | `POST /pdv/sessions/{id}/sales` — venda com baixa de estoque |
| `ECOMMERCE_READ` | Acesso ao endpoint stub `GET /ecommerce/carts` |
| `FINANCEIRO_READ` | Acesso ao endpoint stub `GET /financeiro/cash-flow` |
| `LOGISTICA_READ` | Acesso ao endpoint stub `GET /logistica/shipments` |

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
docker compose up -d        # sobe PostgreSQL (5435) + Redis (6382)
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
