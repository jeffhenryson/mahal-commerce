package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.converter.RoleDTOConverter;
import com.securityspring.adapter.in.dtos.request.RoleRequest;
import com.securityspring.adapter.in.dtos.response.RoleResponseDTO;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.ports.in.RoleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/roles")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleUseCase roleUseCase;
    private final RoleDTOConverter converter;

    public RoleController(RoleUseCase roleUseCase, RoleDTOConverter converter) {
        this.roleUseCase = roleUseCase;
        this.converter = converter;
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

    @Operation(summary = "Cria uma nova role")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = RoleResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Role já existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_CREATE')")
    public ResponseEntity<RoleResponseDTO> create(@Valid @RequestBody RoleRequest request) {
        Role created = roleUseCase.createRole(request.getName());
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
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        roleUseCase.deleteRole(name);
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
            @PathVariable String permissionName) {
        roleUseCase.assignPermission(roleName, permissionName);
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
            @PathVariable String permissionName) {
        roleUseCase.removePermission(roleName, permissionName);
        return ResponseEntity.noContent().build();
    }

}
