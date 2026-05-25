package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.request.LoginRequest;
import com.securityspring.adapter.in.dtos.request.LogoutRequest;
import com.securityspring.adapter.in.dtos.request.RefreshRequest;
import com.securityspring.adapter.in.dtos.request.RegisterRequest;
import com.securityspring.adapter.in.dtos.request.ResendVerificationRequest;
import com.securityspring.adapter.in.dtos.request.VerifyEmailRequest;
import com.securityspring.adapter.in.dtos.response.TokenPairResponse;
import com.securityspring.core.domain.model.TokenPair;
import com.securityspring.core.ports.in.AuthUseCase;
import com.securityspring.core.ports.in.UserUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthUseCase authUseCase;
    private final UserUseCase userUseCase;

    public AuthController(AuthUseCase authUseCase, UserUseCase userUseCase) {
        this.authUseCase = authUseCase;
        this.userUseCase = userUseCase;
    }

    @Operation(summary = "Login e emissão de tokens (access + refresh)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login bem-sucedido", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPairResponse.class), examples = @ExampleObject(value = "{\n  \"accessToken\": \"<JWT>\",\n  \"refreshToken\": \"<OPAQUE>\",\n  \"tokenType\": \"Bearer\"\n}"))),
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
            @ApiResponse(responseCode = "200", description = "Tokens emitidos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TokenPairResponse.class))),
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

    @Operation(summary = "Autoregistro de usuário — cria conta desabilitada e envia código de verificação")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Conta criada — verifique o email"),
            @ApiResponse(responseCode = "409", description = "Username ou email já existe", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        userUseCase.registerUser(request.getUsername(), request.getPassword(), request.getEmail(),
                java.util.List.of());
        return ResponseEntity.status(201).build();
    }

    @Operation(summary = "Confirma email com código de 6 dígitos recebido por email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Email confirmado — conta ativada"),
            @ApiResponse(responseCode = "400", description = "Código inválido ou expirado", content = @Content)
    })
    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        userUseCase.verifyEmail(request.getCode());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reenvia código de verificação para o email informado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Código reenviado"),
            @ApiResponse(responseCode = "404", description = "Email não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Email já verificado", content = @Content)
    })
    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        userUseCase.resendVerification(request.getEmail());
        return ResponseEntity.noContent().build();
    }
}
