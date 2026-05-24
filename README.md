## Visão Geral
- Spring Boot 4.0.6 (Java 21)
- Arquitetura Hexagonal (Ports & Adapters)
- Spring Security (BCrypt) — endpoints atualmente liberados (`permitAll`) para facilitar testes
- Persistência com JPA + H2 (em memória)
- Seed automático: cria `admin/admin` com `ROLE_ADMIN`
- OpenAPI (Springdoc) com Swagger UI

## Stack
- Java 21, Maven
- Spring Boot: Web MVC, Data JPA, Security, Validation
- Banco: H2 (console habilitado em `/h2-console`)
- OpenAPI: `springdoc-openapi-starter-webmvc-ui`

## Arquitetura (Hexagonal)

Pastas principais:
- `adapter/in`: controladores REST, DTOs e conversões
- `core`: regras de domínio, ports e serviço de aplicação
- `adapter/out`: entidades JPA e repositórios (implementações das ports)
- `infra/config`: `SecurityConfig`, `BeanConfig` e `SeedConfig`
- `infra/handler`: tratamento global de exceções

## Endpoints
Base path: `http://localhost:8080`

Endpoints de Usuários (`/users`):
- `POST /users` — cria usuário
  - Body: `{ "username": "string(3..80)", "password": "string(3..120)" }`
  - 201 Created com corpo `UserResponseDTO`
  - 409 Conflict se `username` já existir
- `POST /users/{username}/roles/{roleName}` — atribui role a um usuário (cria a role se não existir)
  - 204 No Content
  - 404 Not Found se usuário não existir
- `GET /users/{id}` — busca usuário por id
  - 200 OK com `UserResponseDTO`
  - 404 Not Found se não existir
- `GET /users` — lista todos os usuários
  - 200 OK com `List<UserResponseDTO>`
- `DELETE /users/{id}` — remove usuário por id
  - 204 No Content

Formato de `UserResponseDTO`:
```json
{
  "id": 1,
  "username": "john",
  "roles": ["ROLE_USER", "ROLE_ADMIN"]
}
```

Erros (handler global):
- 404 Not Found: usuário/role não encontrado
- 409 Conflict: username já existe

## Segurança
- `BCryptPasswordEncoder` configurado para armazenar senhas com hash
- `httpBasic` está habilitado, porém, a política atual é `permitAll()` para todos os endpoints (útil para desenvolvimento). Ajuste em `infra/config/SecurityConfig.java` para proteger rotas quando desejar.
- CSRF desabilitado e H2 Console liberado

## Banco de Dados
- H2 em memória (config padrão):
  - URL: `jdbc:h2:mem:demo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE`
  - Usuário: `sa` | Senha: em branco
- Console H2: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:demo`)

Seed automático (em `SeedConfig`):
- Cria o usuário `admin` com senha `admin` e atribui `ROLE_ADMIN` na inicialização, caso ainda não exista

## Documentação (Swagger)
- UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Como Executar
Pré-requisitos: JDK 21 e Maven Wrapper 

Execução direta:
```bash
./mvnw spring-boot:run
```

Build do JAR e execução:
```bash
./mvnw clean package -DskipTests
java -jar target/security-spring-0.0.1-SNAPSHOT.jar
```

Testes:
```bash
./mvnw test
```

## Exemplos com cURL
Criar usuário:
```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"secret"}'
```

Atribuir role:
```bash
curl -X POST http://localhost:8080/users/john/roles/ROLE_USER
```

Buscar por id:
```bash
curl http://localhost:8080/users/1
```

Listar:
```bash
curl http://localhost:8080/users
```

Remover:
```bash
curl -X DELETE http://localhost:8080/users/1
```
