# Handoff — security-spring

> Data: 2026-05-27  
> Branch: main  
> Último commit antes desta sessão: `4b5b814 refactor(arch): reorganiza pacotes para arquitetura hexagonal limpa`

---

## O que foi feito nestas sessões

### Batch anterior (commits 862a1dc → 4b5b814) — já consolidado
16 melhorias de segurança, arquitetura e qualidade aplicadas e todos os testes rodando (159 passando).

### Sessão atual — NÃO COMMITADO AINDA

#### Testes novos escritos (8 arquivos, ~60 testes)

| Arquivo | Cobertura | Estado |
|---|---|---|
| `PasswordPolicyTest` | `PasswordPolicy.isValid()` — regras de complexidade, comprimento, null | ✅ passa |
| `InMemoryLoginAttemptAdapterTest` | lockout, expiração, isolamento por usuário | ✅ passa |
| `InMemoryTokenBlocklistAdapterTest` | threshold, idempotência, eviction | ✅ passa |
| `InMemoryLoginRateLimiterAdapterTest` | janela deslizante, isolamento por IP | ✅ passa |
| `GlobalExceptionHandlerTest` | todos os 15 mapeamentos de exceção → status + código | ✅ passa |
| `RegistrationControllerTest` | register/verifyEmail/resend — happy + error paths | ✅ passa |
| `LoginRateLimitingFilterTest` | paths filtrados vs. bypass, 429 + Retry-After | ✅ passa |
| `CustomUserDetailsServiceTest` | authorities, disabled, UsernameNotFoundException | ✅ passa |

#### CLI Spring Shell — **EM ANDAMENTO / QUEBRADO**

Adicionado `spring-shell-starter:4.0.0` ao `pom.xml`. Arquivos criados:
- `src/main/java/com/securityspring/infra/cli/AdminShellCommands.java`
- `src/main/java/com/securityspring/infra/cli/ShellConfig.java`
- `src/main/resources/application-shell.properties`
- `src/test/resources/application.properties`
- `src/test/resources/application-dev.properties`

**Problema atual:** Spring Shell 4.0.0 tem um bug com Spring Boot 4.0.6:
`JLineShellAutoConfiguration.commandCompleter` usa `@ConditionalOnMissingBean` que falha ao
introspectar a classe porque `org.jline.reader.Parser` **não está no classpath**.

```
Caused by: java.lang.NoClassDefFoundError: org/jline/reader/Parser
Caused by: java.lang.ClassNotFoundException: org.jline.reader.Parser
```

Isso quebra TODOS os `@SpringBootTest`. A tentativa de excluir
`SpringShellAutoConfiguration` via `src/test/resources/application-dev.properties` não resolveu.

---

## Estado atual dos testes

```
./mvnw clean test  →  BUILD FAILURE
232 total, ~36 erros (todos por causa do Spring Shell quebrando o contexto)
Testes unitários puros: TODOS passando
Testes @SpringBootTest: TODOS falhando por IllegalStateException no Spring Shell
```

---

## Opções para resolver o Spring Shell

### Opção A — Adicionar JLine explicitamente (mais simples, mantém Spring Shell 4.0.0)

```xml
<!-- no pom.xml, após spring-shell-starter -->
<dependency>
    <groupId>org.jline</groupId>
    <artifactId>jline</artifactId>
    <version>3.28.0</version>  <!-- verificar versão compatível -->
    <optional>true</optional>
</dependency>
```

Verificar se resolve o `ClassNotFoundException`. Se sim, os testes voltam a passar.

### Opção B — Downgrade para Spring Shell 3.3.4 (mais estável, API conhecida)

Spring Shell 3.3.4 usa Spring Framework 6.x. Spring Boot 4 usa Framework 7.x.
Pode não compilar, mas vale tentar.

API 3.x: `@ShellComponent`, `@ShellMethod`, `@ShellOption` (de `org.springframework.shell.standard`).

Se funcionar, reescrever `AdminShellCommands.java` com:
```java
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

@ShellComponent
public class AdminShellCommands {
    @ShellMethod(key = "hash-password", value = "...")
    public String hashPassword(@ShellOption String password) { ... }
}
```

### Opção C — Remover Spring Shell, usar ApplicationRunner (zero deps extras)

Implementar CLI sem Spring Shell — `@Component @Profile("shell")` com `ApplicationRunner`:

```java
@Component
@Profile("shell")
public class AdminCliRunner implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        String cmd = args.getNonOptionArgs().isEmpty() ? "help" : args.getNonOptionArgs().get(0);
        switch (cmd) {
            case "hash-password" -> System.out.println(hashPort.hash(args.getOptionValues("password").get(0)));
            case "create-admin"  -> { ... }
            // ...
        }
    }
}
```

Uso: `java -jar app.jar --spring.profiles.active=dev,shell hash-password --password MinhaS3nh@`

---

## Prompt para próxima sessão

```
Você está num projeto Spring Boot 4 (Java 21) com arquitetura hexagonal.
Branch: main. Último commit: 4b5b814.

SITUAÇÃO ATUAL:
Há mudanças não commitadas neste projeto. Os testes estão quebrados por causa
de spring-shell-starter:4.0.0 adicionado ao pom.xml.

PROBLEMA IMEDIATO A RESOLVER:
Spring Shell 4.0.0 + Spring Boot 4.0.6 é incompatível: JLineShellAutoConfiguration
falha porque org.jline.reader.Parser não está no classpath. Isso quebra todos os
@SpringBootTest com IllegalStateException.

OBJETIVO:
1. Resolver o Spring Shell (veja NEXT_SESSION.md na raiz do projeto para as 3 opções)
2. Garantir que ./mvnw clean test passe completamente (exceto os 4 skips já existentes
   por @EnabledIfEnvironmentVariable e AuthFlowPostgresIT)
3. Fazer git commit com tudo limpo

ARQUIVOS RELEVANTES:
- pom.xml — tem spring-shell-starter:4.0.0 (o vilão)
- src/main/java/com/securityspring/infra/cli/AdminShellCommands.java
- src/main/java/com/securityspring/infra/cli/ShellConfig.java
- src/main/resources/application-shell.properties
- src/test/resources/application.properties (tentativa de exclusão)
- src/test/resources/application-dev.properties (tentativa de exclusão)
- NEXT_SESSION.md — documento completo desta sessão

CONTEXTO DOS TESTES:
Os 8 novos testes escritos nesta sessão funcionam quando o Spring Shell não
quebra o contexto. Eles ficam em:
- src/test/java/com/securityspring/core/domain/PasswordPolicyTest.java
- src/test/java/com/securityspring/adapter/out/security/ratelimit/InMemoryLoginAttemptAdapterTest.java
- src/test/java/com/securityspring/adapter/out/security/ratelimit/InMemoryLoginRateLimiterAdapterTest.java
- src/test/java/com/securityspring/adapter/out/security/blocklist/InMemoryTokenBlocklistAdapterTest.java
- src/test/java/com/securityspring/infra/handler/GlobalExceptionHandlerTest.java
- src/test/java/com/securityspring/adapter/in/controller/RegistrationControllerTest.java
- src/test/java/com/securityspring/infra/security/LoginRateLimitingFilterTest.java
- src/test/java/com/securityspring/infra/security/CustomUserDetailsServiceTest.java

COMANDOS DE DIAGNÓSTICO:
./mvnw clean test --no-transfer-progress 2>&1 | grep -E "Tests run:|BUILD|\[ERROR\].*Test"
./mvnw clean test --no-transfer-progress -Dtest="SecuritySpringApplicationTests" 2>&1 | grep "Caused by"
```

---

## Commit recomendado (após resolver Spring Shell)

```bash
git add -A
git commit -m "feat(tests+cli): 8 novos testes de cobertura + CLI Spring Shell

Testes (8 arquivos, ~60 casos):
- PasswordPolicyTest: regras de complexidade, comprimento, null
- InMemoryLoginAttemptAdapterTest: lockout, expiração, isolamento
- InMemoryTokenBlocklistAdapterTest: threshold, idempotência, eviction
- InMemoryLoginRateLimiterAdapterTest: janela deslizante, isolamento por IP
- GlobalExceptionHandlerTest: todos os 15 mapeamentos de exceção
- RegistrationControllerTest: register/verifyEmail/resend — happy + error paths
- LoginRateLimitingFilterTest: paths filtrados, 429 + Retry-After
- CustomUserDetailsServiceTest: authorities, disabled, UsernameNotFound

CLI Spring Shell:
- AdminShellCommands: hash-password, create-admin, enable/disable-user,
  unlock-account, list-sessions, revoke-sessions
- ShellConfig: @Profile('shell') + @EnableCommand
- application-shell.properties: web=none, interactive=true

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```
