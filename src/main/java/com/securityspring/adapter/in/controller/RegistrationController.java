package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.request.RegisterRequest;
import com.securityspring.adapter.in.dtos.request.ResendVerificationRequest;
import com.securityspring.adapter.in.dtos.request.VerifyEmailRequest;
import com.securityspring.core.ports.in.UserUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class RegistrationController {

        private final UserUseCase userUseCase;
        private final List<String> defaultRoles;

        public RegistrationController(UserUseCase userUseCase,
                        @Value("${auth.registration.default-roles:}") String defaultRolesProperty) {
                this.userUseCase = userUseCase;
                // Property: auth.registration.default-roles=ROLE_USER,ROLE_ANOTHER
                // Vazio por padrão — sem role automática no auto-registro (princípio do mínimo
                // privilégio).
                this.defaultRoles = defaultRolesProperty.isBlank()
                                ? List.of()
                                : Arrays.stream(defaultRolesProperty.split(","))
                                                .map(String::trim)
                                                .filter(s -> !s.isBlank())
                                                .collect(Collectors.toList());
        }

        @Operation(summary = "Autoregistro de usuário — cria conta desabilitada e envia código de verificação")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Conta criada — verifique o email"),
                        @ApiResponse(responseCode = "409", description = "Username ou email já existe", content = @Content)
        })
        @PostMapping("/register")
        ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
                userUseCase.registerUser(request.getUsername(), request.getPassword(), request.getEmail(),
                                defaultRoles);
                return ResponseEntity.status(201).build();
        }

        @Operation(summary = "Confirma email com código recebido por email")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Email confirmado — conta ativada"),
                        @ApiResponse(responseCode = "400", description = "Código inválido ou expirado", content = @Content)
        })
        @PostMapping("/verify-email")
        ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
                userUseCase.verifyEmail(request.getCode());
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Reenvia código de verificação para o email informado", description = "Sempre retorna 204 — não revela se o email está cadastrado.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Código reenviado (ou email não encontrado — resposta idêntica por segurança)")
        })
        @PostMapping("/resend-verification")
        ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
                try {
                        userUseCase.resendVerification(request.getEmail());
                } catch (Exception ignored) {
                        // Security: always 204 — never reveal whether email is registered,
                        // verified, on cooldown, or whether delivery failed.
                }
                return ResponseEntity.noContent().build();
        }
}
