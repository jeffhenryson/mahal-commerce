# Template de Maturidade – Autenticação JWT e Engenharia

Objetivo: tornar este projeto um template sólido para novas aplicações, com autenticação JWT, segurança robusta e boas práticas de engenharia. Abaixo, um checklist prático por fases.

## 1) Fundamentos de Projeto
- [ ] Dependências: padronizar starters
  - `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-test`.
  - Remover variantes não padrão (ex.: `*-webmvc`, `*-webmvc-test`, `*-data-jpa-test`).
- [ ] Versão do Spring Boot consistente (ex.: 3.3.x) e Java 21.
- [ ] Estrutura de pacotes clara (adapter in/out, core, infra/security, infra/config, infra/handler, docs, scripts).
- [ ] Perfis: `dev` (H2, console H2, logs verbosos), `hml` (Postgres + Flyway), `prod` (Postgres + Flyway, observabilidade e segurança reforçadas).
- [ ] Migrações Flyway: `ddl-auto=none` em hml/prod; evolução de schema via `db/migration`.
- [x] Seeds condicionados a perfis (ex.: admin/admin apenas em `dev`).

## 2) Autenticação JWT (Stateless)
- [ ] Endpoints de Autenticação
  - [x] `POST /auth/login`: recebe credenciais, emite Access Token (curta duração).
  - [ ] `POST /auth/refresh`: troca Refresh Token por novo par de tokens (rotação).
  - [ ] `POST /auth/logout`: invalida/rotaciona refresh (quando aplicável).
- [ ] Modelo de Tokens
  - Access Token: expiração curta (ex.: 5m–15m), assinado (HS256/RS256). Sem dados sensíveis.
  - Refresh Token: expiração maior (ex.: 7–30d), rotacionado a cada uso; persistido/checado no servidor.
  - Opção de transporte: 
    - Cookies HttpOnly/SameSite=strict (recomendado para SPAs) OU 
    - Header `Authorization: Bearer <token>` (APIs públicas/serviço-a-serviço).
- [ ] Componentes de Segurança
  - [x] `JwtTokenProvider`/`JwtService`: gerar/validar tokens, extrair claims, clock skew.
  - [x] `JwtAuthenticationFilter` (OncePerRequestFilter): extrai Bearer, valida token, popula `SecurityContext`.
  - [ ] `AuthenticationEntryPoint`/`AccessDeniedHandler` customizados: respostas 401/403 padronizadas (JSON).
  - [x] `SecurityFilterChain`: `sessionManagement().sessionCreationPolicy(STATELESS)`, `csrf().disable()`.
  - [ ] CORS configurado por perfil.
- [ ] Gestão de Refresh Tokens
  - Persistência: tabela `refresh_tokens` (user_id, token_hash, expiração, status/rotated_at, user-agent/ip opcionais).
  - Rotação obrigatória: cada uso invalida o anterior e emite um novo.
  - Revogação em massa por usuário (opcional: logout global / suspeita de vazamento).
- [ ] Políticas de Credenciais
  - `BCryptPasswordEncoder` com strength adequado (ex.: 10–12) e verificação de custo.
  - Regras de senha (mínimo, blacklist opcional), fluxo de troca de senha e reset.
  - Rate limiting/brute-force protection em `/auth/login` (ex.: bucket4j/Redis, falhas consecutivas por IP/usuário).

## 3) Autorização por Roles/Privilégios
- [ ] Modelo de autorização
  - `@PreAuthorize` em controladores/serviços críticos (preferir serviço para regra de negócio sensível).
  - Roles normalizadas (`ROLE_ADMIN`, `ROLE_USER`), com `hasRole('ADMIN')`/`hasAnyRole(...)`.
  - (Opcional) Hierarquia de roles (`RoleHierarchy`) quando necessário.
- [ ] Escopo de endpoints
  - Permitir público apenas o essencial (`/auth/**`, documentação se necessário em dev).
  - Demais rotas `authenticated()` com checagens finas por papel.

## 4) CORS, CSRF e Cabeçalhos
- [ ] CORS: permitir origens conhecidas por perfil; métodos e headers estritamente necessários.
- [ ] CSRF: desabilitado em APIs stateless; habilitado conforme padrão em rotas com sessão (se houver).
- [ ] Cabeçalhos de segurança: `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options` (exceto H2-dev), `Content-Security-Policy` conforme front.

## 5) Observabilidade e Operação
- [ ] Actuator: health (`db`, `flyway`), readiness/liveness, métricas básicas, endpoints protegidos.
- [ ] Logs padronizados para autenticação/erros 401/403 (sem dados sensíveis). Correlação de requisições.
- [ ] Auditoria de eventos: logins, falhas de login, trocas de senha, revogações de token.

## 6) Qualidade e Testes
- [ ] Testes de segurança
  - Unitários: `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`.
  - Integração: 401/403/200 em rotas com roles distintas (Testcontainers Postgres).
  - Testes de rotação/revogação de refresh token.
- [ ] Testes de validação e handler global: mensagens claras para erros de entrada/autorização.
- [ ] Análise estática (SpotBugs/Checkstyle/PMD) opcional; Coverage básica.

## 7) Entrega, Infra e Segredos
- [ ] Dockerfile do app (multi-stage), `docker-compose` (Postgres, opcional Redis para rate limiting/sessions).
- [ ] Segredos por env/secret manager; nunca comitar secretos.
- [ ] Perfis em CI/CD: `dev` (build/test), `hml` (deploy em staging) e `prod`.
- [ ] OpenAPI com `securitySchemes` (Bearer JWT) e exemplos; bloquear Swagger em `hml/prod` ou exigir auth.

