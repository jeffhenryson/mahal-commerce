package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.SetConfigRequest;
import com.cernecommerce.core.ports.in.SystemConfigUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/system/config")
@Tag(name = "System Config", description = "Configuração dinâmica do sistema")
public class SystemConfigController {

    private final SystemConfigUseCase systemConfigUseCase;

    public SystemConfigController(SystemConfigUseCase systemConfigUseCase) {
        this.systemConfigUseCase = systemConfigUseCase;
    }

    @Operation(summary = "Retorna flags públicas de configuração — sem autenticação")
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        return ResponseEntity.ok(systemConfigUseCase.getAllPublic());
    }

    @Operation(summary = "Retorna todas as flags de configuração — requer DEV elevado")
    @GetMapping
    @PreAuthorize("hasAuthority('DEV_ELEVATED')")
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(systemConfigUseCase.getAll());
    }

    @Operation(summary = "Atualiza uma flag de configuração — requer DEV elevado")
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('DEV_ELEVATED')")
    public ResponseEntity<Void> set(
            @PathVariable String key,
            @RequestBody @Valid SetConfigRequest request,
            Authentication auth) {
        systemConfigUseCase.set(key, request.value(), auth.getName());
        return ResponseEntity.noContent().build();
    }
}
