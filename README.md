# security-spring

Construí esse projeto para ter uma base sólida de autenticação e autorização que eu possa reutilizar em qualquer novo projeto Spring Boot — sem precisar reinventar a roda a cada vez.

A ideia é simples: toda a lógica de segurança já está pronta e testada. Quando eu começo um projeto novo, só adiciono o domínio de negócio no `core/` e a infraestrutura de auth já funciona do primeiro deploy.

---

## O que tem aqui

**Autenticação completa**
JWT com refresh token opaco (hash SHA-256 no banco, plaintext nunca persiste), rotação a cada uso, detecção de reutilização com revogação automática de todas as sessões, blocklist por threshold `iat` no Redis. Login com Google via ID Token. 2FA com TOTP (Google Authenticator), backup codes e fluxo de duplo TOTP para elevação de privilégio DEV.

**Autorização granular**
RBAC com permissões individuais por role. Toda proteção via `@PreAuthorize("hasAuthority('...')")` — sem `hasRole()`, sem sessão HTTP. Hierarquia `ROLE_USER ⊂ ROLE_ADMIN ⊂ ROLE_DEV`.

**Segurança de produção**
Rate limiting por IP (Redis em hml/prod, in-memory em dev), bloqueio de conta por tentativas, verificação de email com código 12 chars (62 bits de entropia), troca de email com confirmação no novo endereço, reset de senha com token de uso único.

**Infraestrutura**
37 migrations Flyway, 7 schedulers com ShedLock, audit trail de todos os eventos, alertas de segurança por email, métricas Prometheus + dashboard Grafana provisionado automaticamente, feature flags em banco de dados, CLI administrativo.

---

## Tecnologias

| | |
|---|---|
| Java 21 + Spring Boot 4.0.6 | Arquitetura hexagonal (Ports & Adapters) |
| PostgreSQL 16 + Flyway | Redis 7 (cache + blocklist + rate limit) |
| JWT (JJWT 0.12.6) | BCrypt + AES-256-GCM (secret TOTP) |
| JUnit 5 + Mockito + Testcontainers | ArchUnit (regras arquiteturais em teste) |
| Docker Compose | Prometheus + Grafana |

---

## Arquitetura

O `core/` não importa Spring, JPA, Redis nem HTTP — apenas interfaces. O ArchUnit garante isso em tempo de teste.

```
adapter/in  (Controllers, DTOs)
    ↓  UseCase interface
core/  (domain, ports, services)
    ↑  Repository/Port interface
adapter/out  (JPA, Redis, JWT, Email, S3)
```

Trocar PostgreSQL por outro banco, Redis por Caffeine, S3 por storage local — nada disso toca o `core/`.

---

## Rodar localmente

Sem Docker, sem variáveis de ambiente — só Java 21:

```bash
./mvnw spring-boot:run
```

Sobe com H2 em memória. Swagger em `http://localhost:8080/swagger-ui.html`.

Usuários criados automaticamente: `admin / Admin@dev1` e `user / User@dev1`.

---

## Testes

```bash
./mvnw test
```

69 arquivos de teste — unitários, integração (H2), testes de segurança, ArchUnit e Testcontainers (PostgreSQL real, opcional).

---

## Documentação detalhada

A pasta `docs/` tem tudo mapeado: fluxos de autenticação, modelo de domínio, schema do banco, referência de API, configuração por ambiente e muito mais. Disponibilizo mediante contato.

---

## Contato

**Jeff Henryson**
jeffhunbruey@gmail.com · (83) 99669-7177
