# security-spring

Estudo de arquitetura hexagonal (Ports & Adapters) aplicada a uma API REST com camada de segurança completa usando Spring Boot e Spring Security.

O objetivo do projeto é servir de base reutilizável para novas aplicações, com autenticação JWT, controle de acesso por papéis (RBAC), rotação de refresh tokens e migração de schema via Flyway — tudo pronto para trocar a lógica de negócio sem mexer na infraestrutura.

---

## Tecnologias

| Camada | Escolha |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4 (Web, Security, Data JPA, Validation, Actuator) |
| Autenticação | JWT (JJWT 0.12.6) + Refresh Token opaco |
| Banco (dev) | H2 em memória |
| Banco (hml/prod) | PostgreSQL + Flyway |
| Documentação | SpringDoc OpenAPI (Swagger) |
| Build | Maven |
| Container | Docker + Docker Compose |

---

## Arquitetura

O projeto segue a arquitetura hexagonal: o núcleo de negócio não conhece frameworks, banco de dados ou HTTP — ele expõe portas que os adaptadores implementam.

```
┌─────────────────────────────────────────────────────────┐
│                        adapter/in                       │
│          Controllers  ·  DTOs  ·  Converters            │
└────────────────────────────┬────────────────────────────┘
                             │ porta de entrada (UseCase)
┌────────────────────────────▼────────────────────────────┐
│                          core                           │
│        domain/model  ·  ports  ·  service               │
└────────────────────────────┬────────────────────────────┘
                             │ porta de saída (Repository)
┌────────────────────────────▼────────────────────────────┐
│                       adapter/out                       │
│         Entities JPA  ·  Repository impls               │
└─────────────────────────────────────────────────────────┘

infra/
  config/    → SecurityConfig, BeanConfig, OpenApiConfig, SeedConfig
  security/  → JWT, filtros, rate limiting, refresh token
  handler/   → GlobalExceptionHandler (respostas JSON padronizadas)
  audit/     → Listener de eventos de autenticação
```

### Convenção Lombok

| Camada | Abordagem | Motivo |
|---|---|---|
| `core/domain/model` | Manual (sem Lombok) | O core não deve depender de frameworks; modelos de domínio têm métodos semânticos (`changePassword`, `rename`, etc.) |
| `adapter/out/entities` | Lombok (`@Getter @Setter @NoArgsConstructor`) | Entidades JPA são artefatos de infraestrutura — Lombok reduz boilerplate sem impacto no domínio |
| `adapter/in/dtos` | Lombok (`@Data`) | DTOs são simples POJOs de transporte; `@Data` é adequado |

---

## Segurança

### Autenticação
- **Access token** JWT (HS256) de curta duração — configurável via `JWT_ACCESS_TTL_MINUTES`
- **Refresh token** opaco armazenado como hash SHA-256 no banco — nunca o token em claro
- Rotação de refresh token a cada uso (revogação automática do anterior)
- Logout revoga o refresh token imediatamente

### Autorização
- RBAC com `@PreAuthorize` por papel (`ADMIN`, `USER`)
- Sessão stateless — sem cookies, sem estado no servidor

### Proteções adicionais
- Rate limiting em `POST /auth/login` por IP (janela e limite configuráveis)
- CORS por perfil (permissivo em dev, restrito em hml via env)
- CSRF desabilitado (API stateless — sem sessão de formulário)
- Actuator: `health` e `info` públicos; demais endpoints exigem papel `ADMIN`

---

## Setup local

### Pré-requisitos
- Java 21+
- Maven 3.9+ (ou use o wrapper `./mvnw`)
- Docker (apenas para perfil `hml`)

### 1. Clonar e configurar variáveis

```bash
git clone https://github.com/seu-usuario/security-spring.git
cd security-spring
cp .env.example .env
```

O arquivo `.env` já vem com os defaults de dev. Ajuste `JWT_SECRET` se quiser.

### 2. Rodar em dev (H2 em memória)

```bash
./mvnw spring-boot:run
```

| Recurso | URL |
|---|---|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| H2 Console | http://localhost:8080/h2-console |

> JDBC URL do H2: `jdbc:h2:mem:demo` · Usuário: `sa` · Sem senha

O perfil dev cria automaticamente os usuários de seed:

| Usuário | Senha | Papel |
|---|---|---|
| `admin` | `Admin@dev1` | `ADMIN` |
| `user` | `User@dev1` | `USER` |

### 3. Rodar em hml (PostgreSQL)

```bash
docker compose up -d          # sobe o Postgres
SPRING_PROFILES_ACTIVE=hml \
JWT_SECRET=<chave-256-bits> \
./mvnw spring-boot:run
```

O Flyway aplica as migrations automaticamente (`V1__init.sql` a `V5__…`). As migrations criam apenas roles e permissões — **nenhum usuário é criado automaticamente em hml/prod**.

### Criando o primeiro admin (hml/prod)

Após o primeiro deploy, execute o script `scripts/create-first-admin.sql` substituindo o hash bcrypt pelo da senha escolhida:

```bash
# Gerar hash bcrypt
python3 -c "import bcrypt; print(bcrypt.hashpw(b'SUA_SENHA', bcrypt.gensalt(10)).decode())"

# Aplicar no banco
psql $DB_URL -f scripts/create-first-admin.sql
```

---

## Testes

```bash
./mvnw test
```

Os testes de integração com PostgreSQL usam Testcontainers e requerem a variável de ambiente `ENABLE_TC=true` para serem executados:

```bash
ENABLE_TC=true ./mvnw test
```

---
