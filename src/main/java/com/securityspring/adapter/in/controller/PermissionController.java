package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.converter.PermissionDTOConverter;
import com.securityspring.adapter.in.dtos.request.PermissionRequest;
import com.securityspring.adapter.in.dtos.response.PermissionResponseDTO;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Permission;
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

@RestController
@RequestMapping("/permissions")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final PermissionUseCase permissionUseCase;
    private final PermissionDTOConverter converter;

    public PermissionController(PermissionUseCase permissionUseCase, PermissionDTOConverter converter) {
        this.permissionUseCase = permissionUseCase;
        this.converter = converter;
    }

    @Operation(summary = "Lista permissions paginadas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_READ')")
    public ResponseEntity<PageResult<PermissionResponseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<Permission> result = permissionUseCase.listAll(page, Math.min(size, 100));
        PageResult<PermissionResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Cria uma nova permission")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = PermissionResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Permission já existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CREATE')")
    public ResponseEntity<PermissionResponseDTO> create(@Valid @RequestBody PermissionRequest request) {
        Permission created = permissionUseCase.createPermission(request.getName());
        return ResponseEntity.created(URI.create("/permissions/" + created.getName()))
                .body(converter.toResponse(created));
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

}
