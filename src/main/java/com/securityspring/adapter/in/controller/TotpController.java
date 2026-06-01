package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.request.RegenerateBackupCodesRequest;
import com.securityspring.adapter.in.dtos.request.TotpConfirmRequest;
import com.securityspring.adapter.in.dtos.request.TotpDisableRequest;
import com.securityspring.adapter.in.dtos.request.TotpReplaceRequest;
import com.securityspring.adapter.in.dtos.response.TotpConfirmResponseDTO;
import com.securityspring.adapter.in.dtos.response.TotpSetupResponseDTO;
import com.securityspring.adapter.in.dtos.response.TotpStatusResponseDTO;
import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.event.AuditEvent.EventType;
import com.securityspring.core.ports.in.TotpUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/auth/2fa")
@SecurityRequirement(name = "bearerAuth")
public class TotpController {

    private final TotpUseCase totpUseCase;
    private final ApplicationEventPublisher publisher;

    public TotpController(TotpUseCase totpUseCase, ApplicationEventPublisher publisher) {
        this.totpUseCase = totpUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Retorna se o 2FA está ativo para o usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status do 2FA")
    })
    @GetMapping("/status")
    ResponseEntity<TotpStatusResponseDTO> status(Authentication authentication) {
        boolean enabled = totpUseCase.isEnabled(authentication.getName());
        return ResponseEntity.ok(new TotpStatusResponseDTO(enabled));
    }

    @Operation(summary = "Inicia configuração do 2FA — retorna secret e URI para QR code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Secret gerado",
                    content = @Content(schema = @Schema(implementation = TotpSetupResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "2FA já está ativado", content = @Content)
    })
    @PostMapping("/setup")
    ResponseEntity<TotpSetupResponseDTO> setup(Authentication authentication) {
        TotpUseCase.TotpSetupResult result = totpUseCase.setup(authentication.getName());
        return ResponseEntity.ok(new TotpSetupResponseDTO(result.secret(), result.otpauthUri()));
    }

    @Operation(summary = "Confirma o primeiro código TOTP e ativa 2FA — retorna backup codes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "2FA ativado",
                    content = @Content(schema = @Schema(implementation = TotpConfirmResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Código inválido", content = @Content)
    })
    @PostMapping("/confirm")
    ResponseEntity<TotpConfirmResponseDTO> confirm(@Valid @RequestBody TotpConfirmRequest request,
            Authentication authentication) {
        List<String> backupCodes = totpUseCase.confirm(authentication.getName(), request.getCode());
        publisher.publishEvent(AuditEvent.of(EventType.TOTP_ENABLED, authentication.getName()));
        return ResponseEntity.ok(new TotpConfirmResponseDTO(backupCodes));
    }

    @Operation(summary = "Desativa 2FA — exige senha atual e código TOTP")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "2FA desativado"),
            @ApiResponse(responseCode = "400", description = "Senha ou código inválido", content = @Content)
    })
    @DeleteMapping
    ResponseEntity<Void> disable(@Valid @RequestBody TotpDisableRequest request,
            Authentication authentication) {
        totpUseCase.disable(authentication.getName(), request.getCurrentPassword(), request.getCode());
        publisher.publishEvent(AuditEvent.of(EventType.TOTP_DISABLED, authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Troca o dispositivo 2FA — valida código atual, gera novo QR e novos backup codes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Novo secret gerado — confirme com /auth/2fa/confirm",
                    content = @Content(schema = @Schema(implementation = TotpSetupResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Código atual inválido", content = @Content),
            @ApiResponse(responseCode = "400", description = "2FA não está ativo", content = @Content)
    })
    @PostMapping("/replace")
    ResponseEntity<TotpSetupResponseDTO> replace(@Valid @RequestBody TotpReplaceRequest request,
            Authentication authentication) {
        TotpUseCase.TotpSetupResult result = totpUseCase.replaceTotp(
                authentication.getName(), request.getCurrentTotpCode());
        publisher.publishEvent(AuditEvent.of(EventType.TOTP_REPLACED, authentication.getName()));
        return ResponseEntity.ok(new TotpSetupResponseDTO(result.secret(), result.otpauthUri()));
    }

    @Operation(summary = "Regenera backup codes — invalida os anteriores, exige senha atual")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Novos backup codes gerados",
                    content = @Content(schema = @Schema(implementation = TotpConfirmResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Senha inválida ou 2FA não ativado", content = @Content)
    })
    @PostMapping("/backup-codes/regenerate")
    ResponseEntity<TotpConfirmResponseDTO> regenerateBackupCodes(
            @Valid @RequestBody RegenerateBackupCodesRequest request,
            Authentication authentication) {
        List<String> backupCodes = totpUseCase.regenerateBackupCodes(
                authentication.getName(), request.getCurrentPassword());
        publisher.publishEvent(AuditEvent.of(EventType.TOTP_BACKUP_CODES_REGENERATED, authentication.getName()));
        return ResponseEntity.ok(new TotpConfirmResponseDTO(backupCodes));
    }
}
