package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.DevCompleteRequest;
import com.cernecommerce.adapter.in.dtos.request.DevFirstCodeRequest;
import com.cernecommerce.adapter.in.dtos.response.DevAccessTokenResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.DevFirstCodeResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.auth.DevElevationResult;
import com.cernecommerce.core.ports.in.AuthUseCase;
import com.cernecommerce.core.ports.in.TotpUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/dev")
@Tag(name = "DEV Auth", description = "Elevação de acesso para área de desenvolvedor via duplo TOTP consecutivo")
public class DevAuthController {

    private static final int DEV_CHALLENGE_EXPIRES_IN = 90;

    private final TotpUseCase totpUseCase;
    private final AuthUseCase authUseCase;
    private final ApplicationEventPublisher publisher;

    public DevAuthController(TotpUseCase totpUseCase, AuthUseCase authUseCase,
                             ApplicationEventPublisher publisher) {
        this.totpUseCase  = totpUseCase;
        this.authUseCase  = authUseCase;
        this.publisher    = publisher;
    }

    @Operation(summary = "Etapa 1 DEV — valida o primeiro código TOTP e emite devToken (TTL 90s)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Primeiro código válido, aguardar próximo período"),
        @ApiResponse(responseCode = "400", description = "Código TOTP inválido", content = @Content),
        @ApiResponse(responseCode = "403", description = "Acesso negado — requer ROLE_DEV", content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/first-code")
    @PreAuthorize("hasAuthority('ROLE_DEV')")
    public ResponseEntity<DevFirstCodeResponseDTO> firstCode(
            @Valid @RequestBody DevFirstCodeRequest request,
            Authentication authentication) {
        String devToken = totpUseCase.issueDevFirstCode(authentication.getName(), request.getTotpCode());
        return ResponseEntity.ok(new DevFirstCodeResponseDTO(devToken, DEV_CHALLENGE_EXPIRES_IN));
    }

    @Operation(summary = "Etapa 2 DEV — valida o segundo TOTP consecutivo e emite access token DEV-elevado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Elevação DEV concluída — token DEV-elevado emitido"),
        @ApiResponse(responseCode = "400", description = "Código inválido ou não consecutivo", content = @Content),
        @ApiResponse(responseCode = "410", description = "devToken expirado ou já utilizado", content = @Content)
    })
    @PostMapping("/complete")
    public ResponseEntity<DevAccessTokenResponseDTO> complete(
            @Valid @RequestBody DevCompleteRequest request) {
        DevElevationResult result = authUseCase.completeDevElevation(request.getDevToken(), request.getTotpCode());
        publisher.publishEvent(AuditEvent.of(EventType.DEV_ELEVATION_COMPLETED, result.username()));
        return ResponseEntity.ok(new DevAccessTokenResponseDTO(result.devAccessToken(), 3600L));
    }
}
