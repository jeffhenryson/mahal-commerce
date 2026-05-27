package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.request.LoginRequest;
import com.securityspring.adapter.in.dtos.request.LogoutRequest;
import com.securityspring.adapter.in.dtos.request.RefreshRequest;
import com.securityspring.adapter.in.dtos.response.SessionInfoDTO;
import com.securityspring.adapter.in.dtos.response.TokenPairResponseDTO;
import com.securityspring.core.domain.model.auth.TokenPair;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.event.AuditEvent.EventType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthUseCase authUseCase;
    private final ApplicationEventPublisher publisher;
    private final long accessTtlSeconds;

    public AuthController(AuthUseCase authUseCase,
            ApplicationEventPublisher publisher,
            @Value("${jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        this.authUseCase = authUseCase;
        this.publisher = publisher;
        this.accessTtlSeconds = accessTtlMinutes * 60;
    }

    @Operation(summary = "Login e emissão de tokens (access + refresh)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login bem-sucedido", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPairResponseDTO.class), examples = @ExampleObject(value = "{\n  \"accessToken\": \"<JWT>\",\n  \"refreshToken\": \"<OPAQUE>\",\n  \"tokenType\": \"Bearer\",\n  \"expiresIn\": 900\n}"))),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = @Content(schema = @Schema(implementation = com.securityspring.infra.handler.ApiError.class)))
    })
    @PostMapping("/login")
    ResponseEntity<TokenPairResponseDTO> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = authUseCase.login(request.getUsername(), request.getPassword());
        publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_IN, request.getUsername()));
        return ResponseEntity
                .ok(new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds));
    }

    @Operation(summary = "Rotaciona refresh e emite novo access + refresh")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tokens emitidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPairResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Refresh inválido/expirado", content = @Content)
    })
    @PostMapping("/refresh")
    ResponseEntity<TokenPairResponseDTO> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair pair = authUseCase.refresh(request.getRefreshToken());
        return ResponseEntity
                .ok(new TokenPairResponseDTO(pair.getAccessToken(), pair.getRefreshToken(), accessTtlSeconds));
    }

    @Operation(summary = "Revoga o refresh token (logout)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Revogado"),
            @ApiResponse(responseCode = "401", description = "Refresh inválido", content = @Content)
    })
    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request, Authentication authentication) {
        authUseCase.logout(request.getRefreshToken());
        if (authentication != null) {
            publisher.publishEvent(AuditEvent.of(EventType.USER_LOGGED_OUT, authentication.getName()));
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoga todos os refresh tokens do usuário autenticado (logout total)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Todos os tokens revogados"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @DeleteMapping("/sessions")
    ResponseEntity<Void> logoutAll(Authentication authentication) {
        authUseCase.logoutAll(authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.USER_SESSIONS_CLEARED, authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista as sessões ativas do usuário autenticado")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de sessões"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/sessions")
    ResponseEntity<List<SessionInfoDTO>> listSessions(Authentication authentication) {
        List<SessionInfoDTO> sessions = authUseCase.listActiveSessions(authentication.getName())
                .stream().map(SessionInfoDTO::from).toList();
        return ResponseEntity.ok(sessions);
    }
}
