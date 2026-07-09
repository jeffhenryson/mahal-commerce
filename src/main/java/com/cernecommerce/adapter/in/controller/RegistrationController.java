package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.RegisterRequest;
import com.cernecommerce.adapter.in.dtos.request.ResendVerificationRequest;
import com.cernecommerce.adapter.in.dtos.request.VerifyEmailRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class RegistrationController {

        private final UserUseCase userUseCase;
        private final ApplicationEventPublisher publisher;
        private final SystemConfigUseCase systemConfig;
        private final List<String> defaultRoles;

        public RegistrationController(UserUseCase userUseCase,
                        ApplicationEventPublisher publisher,
                        SystemConfigUseCase systemConfig,
                        @Value("${auth.registration.default-roles:}") String defaultRolesProperty) {
                this.userUseCase = userUseCase;
                this.publisher = publisher;
                this.systemConfig = systemConfig;
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
                        @ApiResponse(responseCode = "409", description = "Username ou email já existe", content = @Content),
                        @ApiResponse(responseCode = "503", description = "Registro desabilitado", content = @Content)
        })
        @PostMapping("/register")
        ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
                if (!systemConfig.getBoolean("auth.registration.enabled", true)) {
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
                }
                User registered = userUseCase.registerUser(request.getUsername(), request.getPassword(),
                                request.getEmail(), defaultRoles);
                publisher.publishEvent(AuditEvent.of(EventType.USER_REGISTERED, registered.getUsername()));
                return ResponseEntity.status(201).build();
        }

        @Operation(summary = "Confirma email com código recebido por email")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Email confirmado — conta ativada"),
                        @ApiResponse(responseCode = "400", description = "Código inválido ou expirado", content = @Content)
        })
        @PostMapping("/verify-email")
        ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
                String username = userUseCase.verifyEmail(request.getCode());
                publisher.publishEvent(AuditEvent.of(EventType.USER_EMAIL_VERIFIED, username));
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
                } catch (RuntimeException ignored) {
                        // Security: always 204 — never reveal whether email is registered,
                        // verified, on cooldown, or whether delivery failed.
                }
                return ResponseEntity.noContent().build();
        }
}
