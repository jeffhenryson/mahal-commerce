# Guia de Implementação — Backend Pendentes

> Data: 2026-05-29  
> Base: branch `main` — 232 testes passando, arquitetura hexagonal (ports & adapters)

---

## Mapa de Arquivos do Projeto

```
src/main/java/com/securityspring/
├── adapter/in/controller/          ← Controllers REST (adapter de entrada)
├── adapter/in/dtos/                ← DTOs de request/response
├── adapter/in/converter/           ← Domain ↔ DTO
├── adapter/out/persistence/        ← JPA (entidades, repos, conversores)
├── core/domain/model/              ← Modelos de domínio puros
├── core/domain/event/              ← AuditEvent
├── core/domain/exception/          ← Exceções de domínio
├── core/ports/in/                  ← Interfaces de use case (entrada)
├── core/ports/out/                 ← Interfaces de repositório/serviço (saída)
├── core/service/                   ← Implementações dos use cases
├── infra/audit/                    ← Listeners de auditoria
├── infra/config/security/          ← SecurityConfig, CORS
└── infra/handler/                  ← GlobalExceptionHandler
```

**Regra de ouro:** toda lógica de negócio vai em `core/service/`. O controller só
delega e publica `AuditEvent`. O repositório JPA só implementa os ports de saída.

---

## [ALTA] 1. DELETE /users/{username}/roles/{roleName}

**Objetivo:** remover role de um usuário. Permissão `USER_ROLE_ASSIGN`. 204 sem body.
404 se usuário ou role não encontrado (o GlobalExceptionHandler já trata ambos).

### 1.1 Domínio — adicionar `removeRole` em User

**Arquivo:** `core/domain/model/auth/User.java`

Após o método `addRole(Role role)` (linha 113), adicionar:

```java
public void removeRole(Role role) {
    this.roles.removeIf(r -> r.getName().equals(role.getName()));
}
```

### 1.2 Port de entrada — UserUseCase

**Arquivo:** `core/ports/in/UserUseCase.java`

Adicionar após `assignRole`:

```java
void removeRole(String username, String roleName);
```

### 1.3 Serviço — UserService

**Arquivo:** `core/service/UserService.java`

Adicionar após o método `assignRole` (linha ~193):

```java
@Override
@Transactional
public void removeRole(String username, String roleName) {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    Role role = roleRepository.findByName(roleName)
            .orElseThrow(() -> new RoleNotFoundException(roleName));
    user.removeRole(role);
    userRepository.save(user);
    userCachePort.evict(username);
}
```

### 1.4 AuditEvent — novo tipo de evento

**Arquivo:** `core/domain/event/AuditEvent.java`

No enum `EventType`, adicionar `USER_ROLE_REMOVED`:

```java
USER_ROLE_ASSIGNED, USER_ROLE_REMOVED, USER_ENABLED, USER_DISABLED,
```

### 1.5 Controller — UserController

**Arquivo:** `adapter/in/controller/UserController.java`

Adicionar endpoint após `assignRole` (linha ~80):

```java
@Operation(summary = "Remove uma role do usuário")
@ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Removida"),
        @ApiResponse(responseCode = "404", description = "Usuário ou role não encontrado", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
})
@DeleteMapping("/{username}/roles/{roleName}")
@PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
public ResponseEntity<Void> removeRole(@PathVariable String username, @PathVariable String roleName) {
    useCase.removeRole(username, roleName);
    publisher.publishEvent(AuditEvent.of(EventType.USER_ROLE_REMOVED, username, Map.of("role", roleName)));
    return ResponseEntity.noContent().build();
}
```

**Nenhuma migração de banco necessária.**

---

## [MÉDIA] 2. DELETE /auth/sessions/{id} — Revogar sessão individual

**Objetivo:** revogar uma sessão específica pelo ID. 204 sem body.
404 se o ID não pertence ao usuário autenticado (sem revelar que existe de outro usuário).

### 2.1 Nova exceção de domínio

**Arquivo novo:** `core/domain/exception/auth/SessionNotFoundException.java`

```java
package com.securityspring.core.domain.exception.auth;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException() {
        super("Sessão não encontrada");
    }
}
```

### 2.2 GlobalExceptionHandler — mapear para 404

**Arquivo:** `infra/handler/GlobalExceptionHandler.java`

Adicionar import e handler:

```java
import com.securityspring.core.domain.exception.auth.SessionNotFoundException;

@ExceptionHandler(SessionNotFoundException.class)
public ResponseEntity<ApiError> handleSessionNotFound(SessionNotFoundException ex, HttpServletRequest req) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage(), "SESSION_NOT_FOUND", req);
}
```

### 2.3 Port de saída — RefreshTokenPort

**Arquivo:** `core/ports/out/token/RefreshTokenPort.java`

Adicionar:

```java
/** Revoga sessão pelo ID somente se pertencer ao username informado. Lança SessionNotFoundException caso contrário. */
void revokeByIdForUser(Long id, String username);
```

### 2.4 JPA Repository — query segura

**Arquivo:** `adapter/out/persistence/repository/RefreshTokenJpaRepository.java`

Adicionar:

```java
@Query("SELECT r FROM RefreshTokenEntity r WHERE r.id = :id AND r.user.username = :username AND r.revoked = false AND r.expiresAt > :now")
Optional<RefreshTokenEntity> findActiveByIdAndUsername(
    @Param("id") Long id, @Param("username") String username, @Param("now") Instant now);
```

### 2.5 Implementação do port

**Arquivo:** `adapter/out/persistence/repository/RefreshTokenRepositoryImpl.java`

Adicionar (import de `SessionNotFoundException` também):

```java
import com.securityspring.core.domain.exception.auth.SessionNotFoundException;

@Override
public void revokeByIdForUser(Long id, String username) {
    RefreshTokenEntity rt = refreshRepo
            .findActiveByIdAndUsername(id, username, Instant.now())
            .orElseThrow(SessionNotFoundException::new);
    rt.setRevoked(true);
    rt.setRotatedAt(Instant.now());
    refreshRepo.save(rt);
    log.info("audit.refresh.revokedSingle user={} id={}", username, id);
}
```

### 2.6 Port de entrada — AuthUseCase

**Arquivo:** `core/ports/in/AuthUseCase.java`

Adicionar:

```java
void revokeSession(Long sessionId, String username);
```

### 2.7 Serviço — AuthService

**Arquivo:** `core/service/AuthService.java`

Adicionar (o AuthService já injeta `refreshTokenPort`):

```java
@Override
@Transactional
public void revokeSession(Long sessionId, String username) {
    refreshTokenPort.revokeByIdForUser(sessionId, username);
}
```

### 2.8 Controller — AuthController

**Arquivo:** `adapter/in/controller/AuthController.java`

Adicionar endpoint (e import `org.springframework.web.bind.annotation.PathVariable`):

```java
@Operation(summary = "Revoga uma sessão específica do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Sessão revogada"),
        @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
})
@DeleteMapping("/sessions/{id}")
ResponseEntity<Void> revokeSession(@PathVariable Long id, Authentication authentication) {
    authUseCase.revokeSession(id, authentication.getName());
    return ResponseEntity.noContent().build();
}
```

### 2.9 SecurityConfig — adicionar regra para o novo path

**Arquivo:** `infra/config/security/SecurityConfig.java`

Na seção `authorizeHttpRequests`, adicionar ANTES das regras existentes de `/auth/sessions`:

```java
// DELETE /auth/sessions/{id} deve vir ANTES de /auth/** (permitAll)
.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/auth/sessions/*").authenticated()
```

A linha existente `.requestMatchers(HttpMethod.DELETE, "/auth/sessions").authenticated()` permanece.

**Nenhuma migração de banco necessária.**

---

## [MÉDIA] 3. GET /roles?search={termo}

**Objetivo:** filtro de busca de roles por nome (case-insensitive, substring).
Mesmo padrão que será implementado para `GET /users?search=`.

### 3.1 JPA Repository

**Arquivo:** `adapter/out/persistence/repository/RoleJpaRepository.java`

Adicionar:

```java
@Query("SELECT r FROM RoleEntity r WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%')) ORDER BY r.name")
Page<RoleEntity> findByNameContaining(@Param("search") String search, Pageable pageable);
```

### 3.2 Port de saída — RoleRepository

**Arquivo:** `core/ports/out/role/RoleRepository.java`

Adicionar:

```java
PageResult<Role> findByNameContaining(String search, int page, int size);
```

### 3.3 Implementação — RoleRepositoryImpl

**Arquivo:** `adapter/out/persistence/repository/RoleRepositoryImpl.java`

Adicionar:

```java
@Override
@Transactional(readOnly = true)
public PageResult<Role> findByNameContaining(String search, int page, int size) {
    Page<RoleEntity> p = roleRepo.findByNameContaining(search, PageRequest.of(page, size));
    List<Role> content = p.getContent().stream().map(this::toDomain).toList();
    return new PageResult<>(content, page, size, p.getTotalElements(), p.getTotalPages());
}
```

### 3.4 Port de entrada — RoleUseCase

**Arquivo:** `core/ports/in/RoleUseCase.java`

Adicionar:

```java
PageResult<Role> findByNameContaining(String search, int page, int size);
```

### 3.5 Serviço — RoleService

**Arquivo:** `core/service/RoleService.java`

Adicionar:

```java
@Override
public PageResult<Role> findByNameContaining(String search, int page, int size) {
    return roleRepository.findByNameContaining(search, page, size);
}
```

### 3.6 Controller — RoleController

**Arquivo:** `adapter/in/controller/RoleController.java`

Substituir o método `list` existente:

```java
@GetMapping
@PreAuthorize("hasAuthority('ROLE_READ')")
public ResponseEntity<PageResult<RoleResponseDTO>> list(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    int capped = Math.min(size, 100);
    PageResult<Role> result = (search != null && !search.isBlank())
            ? roleUseCase.findByNameContaining(search.trim(), page, capped)
            : roleUseCase.listAll(page, capped);
    PageResult<RoleResponseDTO> response = new PageResult<>(
            result.content().stream().map(converter::toResponse).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages());
    return ResponseEntity.ok(response);
}
```

**Nenhuma migração de banco necessária.**

---

## [MÉDIA] 4. GET /stats — Totais do dashboard

**Objetivo:** substituir 4 requests separados por um único endpoint.

```json
{ "totalUsers": 42, "activeUsers": 38, "totalRoles": 5, "totalPermissions": 13 }
```

Permissão: `USER_READ` **e** `ROLE_READ` (já existem; usuário admin as tem).

### 4.1 Contar usuários — UserJpaRepository

**Arquivo:** `adapter/out/persistence/repository/UserJpaRepository.java`

Adicionar (Spring Data deriva automaticamente):

```java
long countByEnabledTrue();
```

### 4.2 Port de saída — UserRepository

**Arquivo:** `core/ports/out/user/UserRepository.java`

Adicionar:

```java
long countAll();
long countEnabled();
```

### 4.3 Implementação — UserRepositoryImpl

**Arquivo:** `adapter/out/persistence/repository/UserRepositoryImpl.java`

Adicionar:

```java
@Override
@Transactional(readOnly = true)
public long countAll() {
    return userRepo.count();
}

@Override
@Transactional(readOnly = true)
public long countEnabled() {
    return userRepo.countByEnabledTrue();
}
```

### 4.4 Port de saída — RoleRepository e PermissionRepository

**Arquivo:** `core/ports/out/role/RoleRepository.java`

Adicionar:

```java
long countAll();
```

**Arquivo:** `core/ports/out/role/PermissionRepository.java`

Adicionar:

```java
long countAll();
```

Implementar em `RoleRepositoryImpl` e `PermissionRepositoryImpl`:

```java
@Override
public long countAll() { return roleRepo.count(); }

// PermissionRepositoryImpl
@Override
public long countAll() { return permRepo.count(); }
```

### 4.5 DTO de resposta

**Arquivo novo:** `adapter/in/dtos/response/StatsResponseDTO.java`

```java
package com.securityspring.adapter.in.dtos.response;

public record StatsResponseDTO(
    long totalUsers,
    long activeUsers,
    long totalRoles,
    long totalPermissions
) {}
```

### 4.6 Port de entrada

**Arquivo novo:** `core/ports/in/StatsUseCase.java`

```java
package com.securityspring.core.ports.in;

import com.securityspring.adapter.in.dtos.response.StatsResponseDTO;

public interface StatsUseCase {
    StatsResponseDTO getStats();
}
```

> **Nota de arquitetura:** `StatsResponseDTO` sendo referenciado de dentro de `core/ports/in`
> quebra o isolamento da camada. Alternativa mais pura: criar um record `StatsResult` em
> `core/domain/model/` e converter no controller. Para um DTO simples e sem comportamento,
> ambas as abordagens são aceitáveis — escolha conforme a rigidez desejada.

### 4.7 Serviço — StatsService

**Arquivo novo:** `core/service/StatsService.java`

```java
package com.securityspring.core.service;

import com.securityspring.adapter.in.dtos.response.StatsResponseDTO;
import com.securityspring.core.ports.in.StatsUseCase;
import com.securityspring.core.ports.out.role.PermissionRepository;
import com.securityspring.core.ports.out.role.RoleRepository;
import com.securityspring.core.ports.out.user.UserRepository;

public class StatsService implements StatsUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public StatsService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public StatsResponseDTO getStats() {
        return new StatsResponseDTO(
                userRepository.countAll(),
                userRepository.countEnabled(),
                roleRepository.countAll(),
                permissionRepository.countAll()
        );
    }
}
```

### 4.8 Registrar bean — CoreBeanConfig

**Arquivo:** `infra/config/CoreBeanConfig.java`

Adicionar o bean `StatsService` da mesma forma que os outros services são registrados nesse arquivo.

### 4.9 Controller

**Arquivo novo:** `adapter/in/controller/StatsController.java`

```java
package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.response.StatsResponseDTO;
import com.securityspring.core.ports.in.StatsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@SecurityRequirement(name = "bearerAuth")
public class StatsController {

    private final StatsUseCase statsUseCase;

    public StatsController(StatsUseCase statsUseCase) {
        this.statsUseCase = statsUseCase;
    }

    @Operation(summary = "Totais do dashboard")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('ROLE_READ')")
    public ResponseEntity<StatsResponseDTO> stats() {
        return ResponseEntity.ok(statsUseCase.getStats());
    }
}
```

**Nenhuma migração de banco necessária.**

---

## [BAIXA] 5. POST /auth/2fa/backup-codes/regenerate

**Objetivo:** gerar novos backup codes descartando os anteriores. Requer JWT + senha atual.

### 5.1 Port de entrada — TotpUseCase

**Arquivo:** `core/ports/in/TotpUseCase.java`

Adicionar:

```java
List<String> regenerateBackupCodes(String username, String currentPassword);
```

### 5.2 Serviço — TotpService

**Arquivo:** `core/service/TotpService.java`

Adicionar após o método `disable`:

```java
@Override
@Transactional
public List<String> regenerateBackupCodes(String username, String currentPassword) {
    var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));

    if (!passwordHashPort.matches(currentPassword, user.getPassword())) {
        throw new InvalidPasswordException();
    }

    TotpConfig config = totpConfigRepository.findByUsername(username)
            .orElseThrow(TotpNotEnabledException::new);
    if (!config.enabled()) throw new TotpNotEnabledException();

    List<String> backupCodes = generateBackupCodes();
    totpBackupCodeRepository.deleteByUsername(username);
    totpBackupCodeRepository.saveAll(username, backupCodes);
    return backupCodes;
}
```

### 5.3 Request DTO

**Arquivo novo:** `adapter/in/dtos/request/RegenerateBackupCodesRequest.java`

```java
package com.securityspring.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;

public class RegenerateBackupCodesRequest {

    @NotBlank(message = "Senha atual é obrigatória")
    private String currentPassword;

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
}
```

### 5.4 Controller — TotpController

**Arquivo:** `adapter/in/controller/TotpController.java`

Adicionar import e endpoint:

```java
import com.securityspring.adapter.in.dtos.request.RegenerateBackupCodesRequest;

@Operation(summary = "Regenera backup codes (invalida os anteriores) — exige senha atual")
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Novos backup codes gerados",
                content = @Content(schema = @Schema(implementation = TotpConfirmResponseDTO.class))),
        @ApiResponse(responseCode = "400", description = "Senha inválida ou 2FA não ativado", content = @Content)
})
@PostMapping("/backup-codes/regenerate")
ResponseEntity<TotpConfirmResponseDTO> regenerateBackupCodes(
        @Valid @RequestBody RegenerateBackupCodesRequest request,
        Authentication authentication) {
    List<String> backupCodes = totpUseCase.regenerateBackupCodes(
            authentication.getName(), request.getCurrentPassword());
    publisher.publishEvent(AuditEvent.of(EventType.USER_UPDATED, authentication.getName()));
    return ResponseEntity.ok(new TotpConfirmResponseDTO(backupCodes));
}
```

**Nenhuma migração de banco necessária.**

---

## [BAIXA] 6. GET /audit-logs — Histórico de ações admin

**Objetivo:** persistir eventos de auditoria em banco e expô-los via API paginada.
Requer nova permissão `AUDIT_READ`. Hoje o `AuditEventListener` só loga no SLF4J.

### 6.1 Migração de banco — V18

**Arquivo novo:** `src/main/resources/db/migration/V18__audit_logs.sql`

```sql
CREATE TABLE audit_logs (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(80)  NOT NULL,
    action     VARCHAR(80)  NOT NULL,
    target     VARCHAR(255),
    details    TEXT,
    ip_address VARCHAR(45),
    timestamp  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_username  ON audit_logs (username);
CREATE INDEX idx_audit_logs_action    ON audit_logs (action);
CREATE INDEX idx_audit_logs_timestamp ON audit_logs (timestamp DESC);
```

### 6.2 Migração de banco — V19

**Arquivo novo:** `src/main/resources/db/migration/V19__audit_read_permission.sql`

```sql
INSERT INTO permissions (name) VALUES ('AUDIT_READ');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'AUDIT_READ';
```

### 6.3 Entidade JPA

**Arquivo novo:** `adapter/out/persistence/entity/AuditLogEntity.java`

```java
package com.securityspring.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 255)
    private String target;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;

    // getters e setters omitidos — gerar com IDE
}
```

### 6.4 JPA Repository

**Arquivo novo:** `adapter/out/persistence/repository/AuditLogJpaRepository.java`

```java
package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogEntity, Long> {

    @Query("""
        SELECT a FROM AuditLogEntity a
        WHERE (:username IS NULL OR a.username = :username)
          AND (:action   IS NULL OR a.action   = :action)
        ORDER BY a.timestamp DESC
        """)
    Page<AuditLogEntity> findFiltered(
        @Param("username") String username,
        @Param("action")   String action,
        Pageable pageable);
}
```

### 6.5 Model de domínio

**Arquivo novo:** `core/domain/model/AuditLogEntry.java`

```java
package com.securityspring.core.domain.model;

import java.time.Instant;

public record AuditLogEntry(
    Long id,
    String username,
    String action,
    String target,
    String details,
    String ipAddress,
    Instant timestamp
) {}
```

### 6.6 Port de saída

**Arquivo novo:** `core/ports/out/audit/AuditLogRepository.java`

```java
package com.securityspring.core.ports.out.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;

public interface AuditLogRepository {
    void save(AuditEvent event, String ipAddress);
    PageResult<AuditLogEntry> findFiltered(String username, String action, int page, int size);
}
```

### 6.7 Implementação do port

**Arquivo novo:** `adapter/out/persistence/repository/AuditLogRepositoryImpl.java`

```java
package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.AuditLogEntity;
import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.out.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepo;

    public AuditLogRepositoryImpl(AuditLogJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEvent event, String ipAddress) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUsername(event.username());
        entity.setAction(event.type().name());
        entity.setTarget(resolveTarget(event));
        entity.setDetails(event.details().isEmpty() ? null : event.details().toString());
        entity.setIpAddress(ipAddress);
        entity.setTimestamp(event.timestamp());
        jpaRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLogEntry> findFiltered(String username, String action, int page, int size) {
        Page<AuditLogEntity> p = jpaRepo.findFiltered(username, action, PageRequest.of(page, size));
        List<AuditLogEntry> content = p.getContent().stream()
                .map(e -> new AuditLogEntry(e.getId(), e.getUsername(), e.getAction(),
                        e.getTarget(), e.getDetails(), e.getIpAddress(), e.getTimestamp()))
                .collect(Collectors.toList());
        return new PageResult<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    private String resolveTarget(AuditEvent event) {
        // Extrai o campo "role", "userId", etc. do Map<String, Object> details como target
        Object role = event.details().get("role");
        if (role != null) return "role:" + role;
        return null;
    }
}
```

> **Atenção:** `Propagation.REQUIRES_NEW` no `save` garante que a gravação do log não
> seja revertida junto com a transação principal em caso de erro. O evento de auditoria
> é publicado *após* o commit do controller, mas o listener recebe em contexto transacional.

### 6.8 Atualizar AuditEventListener para persistir

**Arquivo:** `infra/audit/AuditEventListener.java`

```java
package com.securityspring.infra.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.ports.out.audit.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        log.info("audit type={} username={} details={}", event.type(), event.username(), event.details());
        String ip = resolveIp();
        auditLogRepository.save(event, ip);
    }

    private String resolveIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
```

### 6.9 DTO de resposta e port de entrada

**Arquivo novo:** `adapter/in/dtos/response/AuditLogResponseDTO.java`

```java
package com.securityspring.adapter.in.dtos.response;

import com.securityspring.core.domain.model.AuditLogEntry;
import java.time.Instant;

public record AuditLogResponseDTO(
    Long id, String who, String action, String target, String ipAddress, Instant timestamp
) {
    public static AuditLogResponseDTO from(AuditLogEntry e) {
        return new AuditLogResponseDTO(e.id(), e.username(), e.action(), e.target(), e.ipAddress(), e.timestamp());
    }
}
```

**Arquivo novo:** `core/ports/in/AuditLogsUseCase.java`

```java
package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;

public interface AuditLogsUseCase {
    PageResult<AuditLogEntry> list(String username, String action, int page, int size);
}
```

**Arquivo novo:** `core/service/AuditLogsService.java`

```java
package com.securityspring.core.service;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.in.AuditLogsUseCase;
import com.securityspring.core.ports.out.audit.AuditLogRepository;

public class AuditLogsService implements AuditLogsUseCase {

    private final AuditLogRepository repository;

    public AuditLogsService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<AuditLogEntry> list(String username, String action, int page, int size) {
        return repository.findFiltered(username, action, page, size);
    }
}
```

Registrar `AuditLogsService` em `CoreBeanConfig.java`.

### 6.10 Controller

**Arquivo novo:** `adapter/in/controller/AuditLogController.java`

```java
package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.response.AuditLogResponseDTO;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.in.AuditLogsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit-logs")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogsUseCase useCase;

    public AuditLogController(AuditLogsUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Lista histórico de auditoria paginado")
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<PageResult<AuditLogResponseDTO>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<AuditLogEntry> result = useCase.list(userId, action, page, Math.min(size, 100));
        PageResult<AuditLogResponseDTO> response = new PageResult<>(
                result.content().stream().map(AuditLogResponseDTO::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }
}
```

> **Testes:** adicionar `EmailVerificationTestHelper`-like helper para criar entradas de
> auditoria em testes de integração. O `AuditEventListener` precisa de um stub no perfil
> de test que não tente persistir (ou garantir que a tabela exista no H2 de teste via V18).

---

## [BAIXA] 7. POST /users/me/avatar — Upload de foto de perfil

**Contexto:** hoje o avatar fica só no `localStorage` do browser. Para sincronizar entre
dispositivos, precisa de persistência no servidor.

**Decisão de armazenamento:** dois caminhos possíveis.

| Opção | Quando usar |
|-------|-------------|
| **Sistema de arquivos local** | Ambiente single-node, dev/hml, sem infra extra |
| **Object storage (MinIO/S3)** | Multi-instância, escala, produção séria |

Este guia documenta a opção de **sistema de arquivos local** com uma interface (`AvatarStoragePort`)
que permite trocar para S3 depois sem mudar nada além do adapter.

### 7.1 Migração de banco — V20

**Arquivo novo:** `src/main/resources/db/migration/V20__user_avatar.sql`

```sql
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
```

### 7.2 Domínio — User.java

**Arquivo:** `core/domain/model/auth/User.java`

Adicionar campo e factory:

```java
private String avatarUrl;

// Em fromPersisted — adicionar parâmetro avatarUrl
public static User fromPersisted(Long id, String username, String hashedPassword,
        boolean enabled, String email, boolean emailVerified, String pendingEmail,
        Set<Role> roles, String avatarUrl) {
    User u = fromPersisted(id, username, hashedPassword, enabled, email, emailVerified, pendingEmail, roles);
    u.avatarUrl = avatarUrl;
    return u;
}

public void updateAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
}

public String getAvatarUrl() { return avatarUrl; }
```

> Atualizar `UserEntity`, `UserEntityConverter` e `UserResponseDTO` para incluir o campo.

### 7.3 Port de saída — AvatarStoragePort

**Arquivo novo:** `core/ports/out/storage/AvatarStoragePort.java`

```java
package com.securityspring.core.ports.out.storage;

import org.springframework.web.multipart.MultipartFile;

public interface AvatarStoragePort {
    /** Salva o arquivo e retorna a URL pública relativa (ex: /uploads/avatars/uuid.jpg) */
    String store(String username, MultipartFile file);
    /** Remove avatar anterior, se existir */
    void delete(String urlPath);
}
```

### 7.4 Implementação local

**Arquivo novo:** `adapter/out/storage/LocalAvatarStorageAdapter.java`

```java
package com.securityspring.adapter.out.storage;

import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Component
public class LocalAvatarStorageAdapter implements AvatarStoragePort {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB

    private final Path uploadDir;
    private final String publicUrlBase;

    public LocalAvatarStorageAdapter(
            @Value("${avatar.storage-path:./uploads/avatars}") String storagePath,
            @Value("${avatar.public-url-base:/uploads/avatars}") String publicUrlBase) {
        this.uploadDir = Paths.get(storagePath).toAbsolutePath();
        this.publicUrlBase = publicUrlBase;
        try { Files.createDirectories(this.uploadDir); } catch (IOException e) {
            throw new IllegalStateException("Não foi possível criar diretório de avatars", e);
        }
    }

    @Override
    public String store(String username, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido: " + contentType);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Arquivo muito grande (máx 2 MB)");
        }
        String ext = contentType.substring(contentType.lastIndexOf('/') + 1);
        String filename = username + "_" + UUID.randomUUID() + "." + ext;
        Path dest = uploadDir.resolve(filename);
        try { Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new RuntimeException("Erro ao salvar avatar", e); }
        return publicUrlBase + "/" + filename;
    }

    @Override
    public void delete(String urlPath) {
        if (urlPath == null) return;
        String filename = urlPath.substring(urlPath.lastIndexOf('/') + 1);
        Path target = uploadDir.resolve(filename);
        try { Files.deleteIfExists(target); } catch (IOException ignored) {}
    }
}
```

Expor os arquivos como recurso estático em `application.properties`:

```properties
spring.web.resources.static-locations=classpath:/static/,file:./uploads/
```

### 7.5 Port de entrada e serviço

**Arquivo:** `core/ports/in/UserUseCase.java`

Adicionar:

```java
User uploadAvatar(String username, org.springframework.web.multipart.MultipartFile file);
```

**Arquivo:** `core/service/UserService.java`

Adicionar dependência `AvatarStoragePort` no construtor e implementação:

```java
@Override
@Transactional
public User uploadAvatar(String username, MultipartFile file) {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserNotFoundException(username));
    if (user.getAvatarUrl() != null) {
        avatarStoragePort.delete(user.getAvatarUrl());
    }
    String url = avatarStoragePort.store(username, file);
    user.updateAvatar(url);
    return userRepository.save(user);
}
```

### 7.6 Controller

**Arquivo:** `adapter/in/controller/UserController.java`

Adicionar endpoint (sem body JSON — usa `multipart/form-data`):

```java
@Operation(summary = "Faz upload do avatar do usuário autenticado")
@PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<UserResponseDTO> uploadAvatar(
        @RequestPart("file") MultipartFile file,
        Authentication authentication) {
    User updated = useCase.uploadAvatar(authentication.getName(), file);
    return ResponseEntity.ok(converter.toResponse(updated));
}
```

> **Nota de produção:** com múltiplas instâncias (k8s, ECS), o sistema de arquivos local
> não funciona. Troque `LocalAvatarStorageAdapter` por um adapter de S3/MinIO implementando
> o mesmo `AvatarStoragePort`. Nenhum outro arquivo precisa ser alterado.

---

## [SEGURANÇA] 8. Refresh token em HttpOnly cookie

**Contexto:** hoje o `refreshToken` é retornado no JSON e o frontend o guarda em `localStorage`,
que é acessível via JavaScript (vulnerável a XSS). A solução é mover o token para um cookie
`HttpOnly` gerenciado pelo browser.

**Impacto:** requer mudanças coordenadas em backend e frontend. Sugestão: feature flag
por propriedade para migração gradual.

### 8.1 Implicações de CORS

`allowCredentials(true)` é obrigatório para que o browser envie cookies cross-origin.
Isso **incompatível com** `allowedOrigins("*")` — é necessário especificar as origens
exatas.

**Arquivo:** `infra/config/security/SecurityConfig.java`

```java
// No método corsConfigurationSource:
config.setAllowCredentials(true);
// Garantir que allowedOrigins não contenha "*" — use propriedade cors.allowed-origins
```

**Arquivo:** `src/main/resources/application.properties`

```properties
# Nunca usar * quando credentials=true
cors.allowed-origins=http://localhost:4200
```

### 8.2 Login — setar cookie na resposta

**Arquivo:** `adapter/in/controller/AuthController.java`

Injetar `@Value("${cookie.secure:false}") boolean cookieSecure` no construtor.

Modificar o método `login` para aceitar `HttpServletResponse`:

```java
@PostMapping("/login")
ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    LoginResponse loginResponse = authUseCase.login(request.getUsername(), request.getPassword());
    if (loginResponse.twoFactorRequired()) {
        return ResponseEntity.ok(new TwoFactorChallengeResponseDTO("PENDING_2FA", loginResponse.challengeToken(), 300));
    }
    TokenPair pair = loginResponse.tokenPair();
    publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_IN, request.getUsername()));
    addRefreshCookie(response, pair.getRefreshToken());
    // Retornar apenas o accessToken no body; refreshToken vai no cookie
    return ResponseEntity.ok(new TokenPairResponseDTO(pair.getAccessToken(), null, accessTtlSeconds));
}

private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
    String cookieValue = "refreshToken=" + refreshToken
            + "; Path=/auth/refresh; HttpOnly"
            + (cookieSecure ? "; Secure" : "")
            + "; SameSite=Strict";
    response.addHeader("Set-Cookie", cookieValue);
}
```

> **Path=/auth/refresh** limita o cookie apenas ao endpoint de refresh,
> reduzindo a superfície de ataque. O browser não envia o cookie em outros requests.

### 8.3 Refresh — ler token do cookie

**Arquivo:** `adapter/in/controller/AuthController.java`

Modificar `refresh` para aceitar cookie OU body (retrocompatível):

```java
@PostMapping("/refresh")
ResponseEntity<TokenPairResponseDTO> refresh(
        @RequestBody(required = false) RefreshRequest body,
        @CookieValue(name = "refreshToken", required = false) String cookieToken,
        HttpServletResponse response) {
    String token = (cookieToken != null) ? cookieToken
            : (body != null ? body.getRefreshToken() : null);
    if (token == null) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    TokenPair pair = authUseCase.refresh(token);
    addRefreshCookie(response, pair.getRefreshToken());
    return ResponseEntity.ok(new TokenPairResponseDTO(pair.getAccessToken(), null, accessTtlSeconds));
}
```

### 8.4 Logout — limpar cookie

```java
@PostMapping("/logout")
ResponseEntity<Void> logout(
        @RequestBody(required = false) LogoutRequest body,
        @CookieValue(name = "refreshToken", required = false) String cookieToken,
        HttpServletResponse response,
        Authentication authentication) {
    String token = (cookieToken != null) ? cookieToken
            : (body != null ? body.getRefreshToken() : null);
    if (token != null) authUseCase.logout(token);
    // Apagar cookie
    String expired = "refreshToken=; Path=/auth/refresh; HttpOnly; Max-Age=0"
            + (cookieSecure ? "; Secure" : "") + "; SameSite=Strict";
    response.addHeader("Set-Cookie", expired);
    if (authentication != null) {
        publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_OUT, authentication.getName()));
    }
    return ResponseEntity.noContent().build();
}
```

### 8.5 Propriedades de configuração

```properties
# application.properties (dev)
cookie.secure=false

# application-hml.properties / application-prod.properties
cookie.secure=true
```

### 8.6 Mudanças no frontend Angular

O frontend precisa:
1. Parar de escrever/ler `refreshToken` do `localStorage`
2. Adicionar `withCredentials: true` nas chamadas de `/auth/login`, `/auth/refresh` e `/auth/logout`
3. O interceptor de renovação de token não precisa mais ler o token — o browser envia automaticamente

---

## [DOCS] 9. GET /users?search & ?enabled — Documentar e implementar

**Contexto:** o frontend (`UsersAdminService`) já envia `search` e `enabled`, mas o
backend ignora esses params e a OpenAPI só documenta `page` e `size`.

### 9.1 JPA — query com filtros opcionais

**Arquivo:** `adapter/out/persistence/repository/UserJpaRepository.java`

```java
@Query("""
    SELECT u.id FROM UserEntity u
    WHERE (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%')))
      AND (:enabled IS NULL OR u.enabled = :enabled)
    ORDER BY u.id
    """)
Page<Long> findFilteredIds(
    @Param("search")  String search,
    @Param("enabled") Boolean enabled,
    Pageable pageable);
```

### 9.2 Port de saída — UserRepository

```java
PageResult<User> findFiltered(String search, Boolean enabled, int page, int size);
```

### 9.3 Implementação — UserRepositoryImpl

```java
@Override
@Transactional(readOnly = true)
public PageResult<User> findFiltered(String search, Boolean enabled, int page, int size) {
    Page<Long> idPage = userRepo.findFilteredIds(search, enabled, PageRequest.of(page, size));
    List<User> users = idPage.isEmpty()
            ? List.of()
            : userRepo.findAllWithRolesByIdIn(idPage.getContent())
                      .stream().map(converter::toDomain).collect(Collectors.toList());
    return new PageResult<>(users, page, size, idPage.getTotalElements(), idPage.getTotalPages());
}
```

### 9.4 Port de entrada — UserUseCase

```java
PageResult<User> findFiltered(String search, Boolean enabled, int page, int size);
```

### 9.5 Serviço — UserService

```java
@Override
public PageResult<User> findFiltered(String search, Boolean enabled, int page, int size) {
    // Delegar para findAll quando sem filtros ativos (permite query mais simples)
    boolean noFilters = (search == null || search.isBlank()) && enabled == null;
    if (noFilters) return userRepository.findAll(page, size);
    return userRepository.findFiltered(
            (search != null && !search.isBlank()) ? search.trim() : null,
            enabled, page, size);
}
```

### 9.6 Controller — UserController

Substituir o método `list` existente:

```java
@GetMapping
@PreAuthorize("hasAuthority('USER_READ')")
public ResponseEntity<PageResult<UserResponseDTO>> list(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    PageResult<User> result = useCase.findFiltered(search, enabled, page, Math.min(size, 100));
    PageResult<UserResponseDTO> response = new PageResult<>(
            result.content().stream().map(converter::toResponse).toList(),
            result.page(), result.size(), result.totalElements(), result.totalPages());
    return ResponseEntity.ok(response);
}
```

A documentação OpenAPI é gerada automaticamente a partir dos `@RequestParam` — os novos
parâmetros aparecerão no Swagger UI sem configuração adicional.

---

## Ordem de Implementação Sugerida

| # | Item | Esforço | Impacto |
|---|------|---------|---------|
| 1 | DELETE /users/{username}/roles/{roleName} | Baixo (5 arquivos) | Alto — frontend esperando |
| 2 | GET /users?search & enabled | Baixo (5 arquivos) | Alto — já enviado pelo frontend |
| 3 | GET /roles?search | Baixo (5 arquivos) | Médio |
| 4 | DELETE /auth/sessions/{id} | Médio (7 arquivos + exceção) | Médio |
| 5 | GET /stats | Médio (8 arquivos) | Médio — 4 requests → 1 |
| 6 | POST /auth/2fa/backup-codes/regenerate | Baixo (3 arquivos) | Baixo |
| 7 | GET /audit-logs | Alto (12 arquivos + 2 migrations) | Baixo/Médio |
| 8 | HttpOnly cookie | Médio (2 arquivos) | Alto — **requer frontend** |
| 9 | POST /users/me/avatar | Alto (8 arquivos + migration) | Baixo — `localStorage` ainda funciona |

---

## Checklist de Testes por Item

- **1 (removeRole):** criar teste de integração similar ao `assignRole` existente — POST depois DELETE, verificar 404 para role inexistente
- **2 (sessions/{id}):** verificar que DELETE de sessão alheia retorna 404 (não 403)
- **3 (roles search):** verificar busca case-insensitive, busca vazia retorna tudo
- **4 (stats):** criar 2 usuários (1 disabled), 1 role, verificar totais
- **5 (backup codes):** regenerar, verificar que codes antigos não funcionam
- **6 (audit-logs):** verificar persistência após login, listagem paginada, filtro por action
- **7 (cookie):** verificar `Set-Cookie` na resposta de login, `withCredentials` no Angular
- **8 (avatar):** upload de JPEG válido, rejeição de arquivo > 2 MB, rejeição de PDF
