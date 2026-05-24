package com.security_spring.adapter.in.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.security_spring.adapter.in.converter.UserDTOConverter;
import com.security_spring.adapter.in.dtos.UserRequestDTO;
import com.security_spring.adapter.in.dtos.UserResponseDTO;
import com.security_spring.core.domain.model.User;
import com.security_spring.core.ports.in.UserUseCase;

import java.net.URI;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserUseCase useCase;
    private final UserDTOConverter converter;

    public UserController(UserUseCase useCase, UserDTOConverter converter) {
        this.useCase = useCase;
        this.converter = converter;
    }

    @Operation(summary = "Cria um novo usuário")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Criado",
            content = @Content(schema = @Schema(implementation = com.security_spring.adapter.in.dtos.UserResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Username já existe", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão (requer ADMIN)", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO request) {
        User created = useCase.createUser(request.getUsername(), request.getPassword());
        UserResponseDTO body = converter.toResponse(created);
        return ResponseEntity.created(URI.create("/users/" + body.getId())).body(body);
    }

    @Operation(summary = "Atribui uma role ao usuário")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Atribuída"),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão (requer ADMIN)", content = @Content)
    })
    @PostMapping("/{username}/roles/{roleName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> assignRole(@PathVariable String username, @PathVariable String roleName) {
        useCase.assignRole(username, roleName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Busca usuário por id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = com.security_spring.adapter.in.dtos.UserResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<UserResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(useCase.getUserById(id)));
    }

    @Operation(summary = "Lista todos os usuários")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<java.util.List<UserResponseDTO>> list() {
        return ResponseEntity.ok(
            useCase.listAll().stream()
                .map(converter::toResponse)
                .collect(Collectors.toList())
        );
    }

    @Operation(summary = "Remove usuário por id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Removido"),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão (requer ADMIN)", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        useCase.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

