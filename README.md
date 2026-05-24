## Visão Geral
- Java 21, Maven
- Spring Boot (Web, Data JPA, Security, Validation, Actuator)
- Arquitetura Hexagonal (Ports & Adapters)
- Segurança: JWT (Bearer), RBAC por roles com `@PreAuthorize`, rate limiting em `/auth/login`
- Persistência: H2 (dev) e Postgres (hml) via perfis + Flyway (V1 schema, V2 refresh tokens)
- OpenAPI (Springdoc) com esquema Bearer JWT (Swagger habilitado apenas em dev)

## Arquitetura
- `adapter/in`: controladores REST, DTOs e conversões
- `core`: regras de domínio, ports e serviço de aplicação
- `adapter/out`: entidades JPA e repositórios (implementam as ports)
- `infra/config`: configs de beans, segurança, openapi, seed
- `infra/security`: filtros (JWT e rate limiting) e serviços de token
- `infra/audit`: auditoria de eventos de autenticação
- `infra/handler`: tratamento global de exceções (JSON)

## Perfis e Execução
- Dev (padrão): H2 em memória, Swagger habilitado, seed `admin/admin`
  ```bash
  ./mvnw spring-boot:run
  # H2 console: http://localhost:8080/h2-console (JDBC jdbc:h2:mem:demo)
  # Swagger UI: http://localhost:8080/swagger-ui/index.html
  ```
- Hml (staging): Postgres + Flyway; Swagger e docs desabilitados por padrão
  ```bash
  docker compose up -d  # sobe Postgres local
  SPRING_PROFILES_ACTIVE=hml \
  DB_URL=jdbc:postgresql://localhost:5432/security \
  DB_USERNAME=postgres DB_PASSWORD=postgres \
  JWT_SECRET=<base64-256bit> \
  ./mvnw spring-boot:run
  ```

## Segurança
- Autenticação: JWT (access de curta duração, refresh opaco no banco)
  - Endpoints `/auth/login`, `/auth/refresh`, `/auth/logout`
- Autorização: `@PreAuthorize`
  - ADMIN: `POST /users`, `POST /users/{username}/roles/{role}`, `DELETE /users/{id}`
  - ADMIN ou USER: `GET /users`, `GET /users/{id}`
- Rate limiting: `/auth/login` limitado por IP (configurável)
- CORS por perfil, CSRF desabilitado (API stateless)
- Actuator: `health`/`info` públicos; demais `/actuator/**` exigem ADMIN

## Variáveis/Propriedades úteis
- JWT: `jwt.secret` (defina um segredo base64 forte em hml), `jwt.access-ttl-minutes`, `jwt.refresh-ttl-days`
- Rate limit login: `rate.limit.login.window-seconds`, `rate.limit.login.max-requests`
- Actuator (dev/hml): `management.endpoints.web.exposure.include=health,info,metrics`

## Documentação (OpenAPI)
- Dev:
  - UI: http://localhost:8080/swagger-ui/index.html
  - JSON: http://localhost:8080/v3/api-docs
- Hml: desabilitado por padrão (habilite via `springdoc.*` se necessário)
- Esquema de segurança: Bearer JWT (`Authorization: Bearer <token>`) aplicado aos controladores protegidos

## Exemplos cURL (Auth)
- Login (tokens):
  ```bash
  curl -s -X POST http://localhost:8080/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}'
  ```
- Refresh:
  ```bash
  curl -s -X POST http://localhost:8080/auth/refresh \
    -H "Content-Type: application/json" \
    -d '{"refreshToken":"<OPAQUE>"}'
  ```
- Acesso com Bearer:
  ```bash
  curl -H "Authorization: Bearer <ACCESS>" http://localhost:8080/users
  ```

## Endpoints de Usuários (resumo)
- `POST /users` (ADMIN) — cria usuário `{ username: 3..80, password: 3..120 }`
- `POST /users/{username}/roles/{role}` (ADMIN) — atribui role
- `GET /users/{id}` (ADMIN/USER) — busca por id
- `GET /users` (ADMIN/USER) — lista todos
- `DELETE /users/{id}` (ADMIN) — remove

## Testes
```bash
./mvnw test
# Testcontainers opcional
ENABLE_TC=true ./mvnw -Dtest=RefreshTokenServiceTest test
```

## Roadmap de Produção (opcional)
- Segredos: gerenciar `JWT_SECRET` (Key Vault/Secrets Manager) e rotacionar; considerar RS256
- Observabilidade: logs estruturados + correlação; métricas customizadas; tracing
- CI/CD: pipeline com build/test/scan; criação de imagem (Dockerfile multi-stage)
- Hardening: headers de segurança; políticas de senha/lockout; rate limit adicional por usuário
- OpenAPI: exemplos completos de request/response para todos os endpoints
