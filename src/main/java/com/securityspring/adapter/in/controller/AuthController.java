package com.security_spring.adapter.in.controller;

import com.security_spring.adapter.in.dtos.request.LoginRequest;
import com.security_spring.adapter.in.dtos.request.LogoutRequest;
import com.security_spring.adapter.in.dtos.request.RefreshRequest;
import com.security_spring.adapter.in.dtos.response.TokenPairResponse;
import com.security_spring.core.domain.model.TokenPair;
import com.security_spring.core.ports.in.AuthUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @Operation(summary = "Login e emissão de tokens (access + refresh)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login bem-sucedido",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = TokenPairResponse.class),
                examples = @ExampleObject(value = "{\n  \"accessToken\": \"<JWT>\",\n  \"refreshToken\": \"<OPAQUE>\",\n  \"tokenType\": \"Bearer\"\n}"))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = authUseCase.login(request.getUsername(), request.getPassword());
        log.info("audit.login.success user={}", request.getUsername());
        return ResponseEntity.ok(new TokenPairResponse(pair.getAccessToken(), pair.getRefreshToken()));
    }

    @Operation(summary = "Rotaciona refresh e emite novo access + refresh")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens emitidos",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = TokenPairResponse.class))),
        @ApiResponse(responseCode = "400", description = "Refresh inválido/expirado", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair pair = authUseCase.refresh(request.getRefreshToken());
        return ResponseEntity.ok(new TokenPairResponse(pair.getAccessToken(), pair.getRefreshToken()));
    }

    @Operation(summary = "Revoga o refresh token (logout)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Revogado"),
        @ApiResponse(responseCode = "400", description = "Refresh inválido", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authUseCase.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revoga todos os refresh tokens do usuário autenticado (logout total)")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Todos os tokens revogados"),
        @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @DeleteMapping("/sessions")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        authUseCase.logoutAll(authentication.getName());
        log.info("audit.logoutAll user={}", authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
