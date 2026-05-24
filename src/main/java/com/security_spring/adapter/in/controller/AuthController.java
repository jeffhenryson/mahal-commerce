package com.security_spring.adapter.in.controller;

import com.security_spring.adapter.in.dtos.LoginRequest;
import com.security_spring.adapter.in.dtos.LogoutRequest;
import com.security_spring.adapter.in.dtos.RefreshRequest;
import com.security_spring.adapter.in.dtos.TokenPairResponse;
import com.security_spring.adapter.in.dtos.TokenResponse;
import com.security_spring.infra.security.JwtService;
import com.security_spring.infra.security.RefreshTokenService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          RefreshTokenService refreshService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
    }

    @Operation(summary = "Login e emissão de tokens (access + refresh)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login bem-sucedido",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = com.security_spring.adapter.in.dtos.TokenPairResponse.class),
                examples = @ExampleObject(value = "{\n  \"accessToken\": \"<JWT>\",\n  \"refreshToken\": \"<OPAQUE>\",\n  \"tokenType\": \"Bearer\"\n}"))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserDetails user = (UserDetails) auth.getPrincipal();
        String access = jwtService.generateAccessToken(user);
        String refresh = refreshService.issueNewRefreshToken(user.getUsername());
        org.slf4j.LoggerFactory.getLogger(AuthController.class).info("audit.login.success user={}", user.getUsername());
        return ResponseEntity.ok(new TokenPairResponse(access, refresh));
    }

    @Operation(summary = "Rotaciona refresh e emite novo access + refresh")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens emitidos",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = com.security_spring.adapter.in.dtos.TokenPairResponse.class))),
        @ApiResponse(responseCode = "400", description = "Refresh inválido/expirado", content = @Content)
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        var result = refreshService.rotateAndGetUsername(request.getRefreshToken());
        // Emite novo access token para o mesmo usuário
        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername(result.username())
                .password("")
                .authorities(java.util.Collections.emptyList()) // authorities serão resolvidas no filtro via UserDetailsService
                .build();
        String newAccess = jwtService.generateAccessToken(userDetails);
        org.slf4j.LoggerFactory.getLogger(AuthController.class).info("audit.refresh.success user={}", result.username());
        return ResponseEntity.ok(new TokenPairResponse(newAccess, result.newToken()));
    }

    @Operation(summary = "Revoga o refresh token")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Revogado"),
        @ApiResponse(responseCode = "400", description = "Refresh inválido", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        refreshService.revoke(request.getRefreshToken());
        org.slf4j.LoggerFactory.getLogger(AuthController.class).info("audit.logout.success");
        return ResponseEntity.noContent().build();
    }
}
