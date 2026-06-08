package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.converter.RoleDTOConverter;
import com.securityspring.adapter.in.dtos.request.RoleRequest;
import com.securityspring.adapter.in.dtos.response.RoleResponseDTO;
import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.event.AuditEvent.EventType;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.domain.exception.ModuleDisabledException;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.core.ports.in.SystemConfigUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/roles")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private static final Set<String> DEV_ONLY_PERMISSIONS = Set.of("DEV_ROLE_MANAGE", "DEV_PERMISSION_MANAGE");

    private final RoleUseCase roleUseCase;
    private final RoleDTOConverter converter;
    private final ApplicationEventPublisher publisher;
    private final SystemConfigUseCase systemConfig;

    public RoleController(RoleUseCase roleUseCase, RoleDTOConverter converter, ApplicationEventPublisher publisher,
            SystemConfigUseCase systemConfig) {
        this.roleUseCase = roleUseCase;
        this.converter = converter;
        this.publisher = publisher;
        this.systemConfig = systemConfig;
    }

    private void checkModuleEnabled() {
        if (!systemConfig.getBoolean("module.roles.enabled", true)) {
            throw new ModuleDisabledException("roles");
        }
    }

    @Operation(summary = "Lista roles paginadas com filtro opcional por nome")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<PageResult<RoleResponseDTO>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        checkModuleEnabled();
        int capped = Math.min(size, 100);
        PageResult<Role> result = (search != null && !search.isBlank())
                ? roleUseCase.findByNameContaining(search.trim(), page, capped)
                : roleUseCase.listAll(page, capped);
        boolean isDevAccess = AuthUtils.isDevAccess(authentication);
        PageResult<RoleResponseDTO> response = new PageResult<>(
                result.content().stream()
                        .filter(r -> isDevAccess || !"ROLE_DEV".equals(r.getName()))
                        .map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Busca uma role pelo nome")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = RoleResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public ResponseEntity<RoleResponseDTO> getByName(@PathVariable String name) {
        checkModuleEnabled();
        return ResponseEntity.ok(converter.toResponse(roleUseCase.findByName(name)));
    }

    @Operation(summary = "Cria uma nova role")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = RoleResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Role já existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('DEV_ROLE_MANAGE')")
    public ResponseEntity<RoleResponseDTO> create(@Valid @RequestBody RoleRequest request,
            Authentication authentication) {
        checkModuleEnabled();
        Role created = roleUseCase.createRole(request.getName());
        publisher.publishEvent(AuditEvent.of(EventType.ROLE_CREATED,
                authentication.getName(), Map.of("role", created.getName())));
        return ResponseEntity.created(URI.create("/roles/" + created.getName()))
                .body(converter.toResponse(created));
    }

    @Operation(summary = "Remove uma role pelo nome")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/{name}")
    @PreAuthorize("hasAuthority('DEV_ROLE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable String name, Authentication authentication) {
        checkModuleEnabled();
        roleUseCase.deleteRole(name);
        publisher.publishEvent(AuditEvent.of(EventType.ROLE_DELETED,
                authentication.getName(), Map.of("role", name)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Atribui uma permission a uma role")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Atribuída"),
            @ApiResponse(responseCode = "404", description = "Role ou permission não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/{roleName}/permissions/{permissionName}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PERMISSIONS')")
    public ResponseEntity<Void> assignPermission(@PathVariable String roleName,
            @PathVariable String permissionName, Authentication authentication) {
        checkModuleEnabled();
        if (DEV_ONLY_PERMISSIONS.contains(permissionName)) {
            boolean hasDevElevated = authentication.getAuthorities().stream()
                    .anyMatch(a -> "DEV_ELEVATED".equals(a.getAuthority()));
            if (!hasDevElevated) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Atribuição de " + permissionName + " requer token DEV elevado");
            }
        }
        roleUseCase.assignPermission(roleName, permissionName);
        publisher.publishEvent(AuditEvent.of(EventType.PERMISSION_ASSIGNED_TO_ROLE,
                authentication.getName(), Map.of("role", roleName, "permission", permissionName)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove uma permission de uma role")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Role não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/{roleName}/permissions/{permissionName}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE_PERMISSIONS')")
    public ResponseEntity<Void> removePermission(@PathVariable String roleName,
            @PathVariable String permissionName, Authentication authentication) {
        checkModuleEnabled();
        if (DEV_ONLY_PERMISSIONS.contains(permissionName)) {
            boolean hasDevElevated = authentication.getAuthorities().stream()
                    .anyMatch(a -> "DEV_ELEVATED".equals(a.getAuthority()));
            if (!hasDevElevated) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Remoção de " + permissionName + " requer token DEV elevado");
            }
        }
        roleUseCase.removePermission(roleName, permissionName);
        publisher.publishEvent(AuditEvent.of(EventType.PERMISSION_REMOVED_FROM_ROLE,
                authentication.getName(), Map.of("role", roleName, "permission", permissionName)));
        return ResponseEntity.noContent().build();
    }
}
