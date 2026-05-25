package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.request.PermissionRequest;
import com.securityspring.adapter.in.dtos.response.PermissionResponseDTO;
import com.securityspring.core.domain.model.Permission;
import com.securityspring.core.ports.in.PermissionUseCase;
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
import java.util.List;

@RestController
@RequestMapping("/permissions")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final PermissionUseCase permissionUseCase;

    public PermissionController(PermissionUseCase permissionUseCase) {
        this.permissionUseCase = permissionUseCase;
    }

    @Operation(summary = "Lista todas as permissions")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<List<PermissionResponseDTO>> list() {
        return ResponseEntity.ok(permissionUseCase.listAll().stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Cria uma nova permission")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Criada",
            content = @Content(schema = @Schema(implementation = PermissionResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Permission já existe", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    public ResponseEntity<PermissionResponseDTO> create(@Valid @RequestBody PermissionRequest request) {
        Permission created = permissionUseCase.createPermission(request.getName());
        return ResponseEntity.created(URI.create("/permissions/" + created.getName()))
                .body(toResponse(created));
    }

    @Operation(summary = "Remove uma permission pelo nome")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Removida"),
        @ApiResponse(responseCode = "404", description = "Não encontrada", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/{name}")
    @PreAuthorize("hasAuthority('PERMISSION_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        permissionUseCase.deletePermission(name);
        return ResponseEntity.noContent().build();
    }

    private PermissionResponseDTO toResponse(Permission permission) {
        PermissionResponseDTO dto = new PermissionResponseDTO();
        dto.setId(permission.getId());
        dto.setName(permission.getName());
        return dto;
    }
}
