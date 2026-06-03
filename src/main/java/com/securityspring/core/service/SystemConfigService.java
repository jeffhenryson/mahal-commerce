package com.securityspring.core.service;

import com.securityspring.core.domain.model.config.SystemConfig;
import com.securityspring.core.ports.in.SystemConfigUseCase;
import com.securityspring.core.ports.out.SystemConfigPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SystemConfigService implements SystemConfigUseCase {

    private static final Set<String> PUBLIC_KEYS = Set.of(
        "auth.google.enabled",
        "auth.google.register.enabled",
        "auth.registration.enabled",
        "auth.forgot-password.enabled"
    );

    private final SystemConfigPort configPort;

    public SystemConfigService(SystemConfigPort configPort) {
        this.configPort = configPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAllPublic() {
        return configPort.findAll().stream()
            .filter(c -> PUBLIC_KEYS.contains(c.key()))
            .collect(Collectors.toMap(SystemConfig::key, SystemConfig::value));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAll() {
        return configPort.findAll().stream()
            .collect(Collectors.toMap(SystemConfig::key, SystemConfig::value));
    }

    @Override
    @Transactional
    public void set(String key, String value, String updatedBy) {
        if (!PUBLIC_KEYS.contains(key)) {
            throw new IllegalArgumentException("Chave de configuração inválida: " + key);
        }
        configPort.save(new SystemConfig(key, value, Instant.now(), updatedBy));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return configPort.getBoolean(key, defaultValue);
    }
}
