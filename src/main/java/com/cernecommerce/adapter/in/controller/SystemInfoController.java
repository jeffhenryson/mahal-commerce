package com.cernecommerce.adapter.in.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/system/info")
@Tag(name = "System Info", description = "Informações do sistema para área DEV")
public class SystemInfoController {

    private final Environment env;

    public SystemInfoController(Environment env) {
        this.env = env;
    }

    @Operation(summary = "Retorna informações do sistema: perfil ativo e status — requer DEV elevado")
    @GetMapping
    @PreAuthorize("hasAuthority('DEV_ELEVATED')")
    public ResponseEntity<Map<String, Object>> info() {
        String[] profiles = env.getActiveProfiles();
        String activeProfile = profiles.length > 0 ? String.join(",", profiles) : "default";
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "profile", activeProfile,
                "profiles", Arrays.asList(profiles)
        ));
    }
}
