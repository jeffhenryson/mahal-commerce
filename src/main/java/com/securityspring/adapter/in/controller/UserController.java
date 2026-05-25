package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.converter.UserDTOConverter;
import com.securityspring.adapter.in.dtos.request.ChangePasswordRequest;
import com.securityspring.adapter.in.dtos.request.UserRequestDTO;
import com.securityspring.adapter.in.dtos.request.UserUpdateRequest;
import com.securityspring.adapter.in.dtos.response.UserResponseDTO;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.User;
import com.securityspring.core.domain.exception.UserNotFoundException;
import com.securityspring.core.ports.in.UserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

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
            content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "409", description = "Username já existe", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO request) {
        User created = useCase.createUser(request.getUsername(), request.getPassword(), request.getRoles());
        UserResponseDTO body = converter.toResponse(created);
        return ResponseEntity.created(URI.create("/users/" + body.getId())).body(body);
    }

    @Operation(summary = "Atribui uma role ao usuário")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Atribuída"),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/{username}/roles/{roleName}")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public ResponseEntity<Void> assignRole(@PathVariable String username, @PathVariable String roleName) {
        useCase.assignRole(username, roleName);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna o perfil do usuário autenticado")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(Authentication authentication) {
        User user = useCase.findByUsername(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(authentication.getName()));
        return ResponseEntity.ok(converter.toResponse(user));
    }

    @Operation(summary = "Troca a senha do usuário autenticado")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Senha alterada"),
        @ApiResponse(responseCode = "400", description = "Senha atual incorreta", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request,
                                                   Authentication authentication) {
        useCase.changeOwnPassword(authentication.getName(),
                request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Busca usuário por id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(useCase.getUserById(id)));
    }

    @Operation(summary = "Lista usuários paginado")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<PageResult<UserResponseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<User> result = useCase.listAll(page, Math.min(size, 100));
        PageResult<UserResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Remove usuário por id")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Removido"),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        useCase.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desativa conta de usuário")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Desativado"),
        @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_STATUS')")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        useCase.setUserEnabled(id, false);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reativa conta de usuário")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Reativado"),
        @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('USER_STATUS')")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        useCase.setUserEnabled(id, true);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Atualiza dados básicos do usuário")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Atualizado",
            content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
        @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
        @ApiResponse(responseCode = "409", description = "Username já existe", content = @Content),
        @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id,
                                                   @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(converter.toResponse(useCase.updateUser(id, request.getUsername())));
    }
}

