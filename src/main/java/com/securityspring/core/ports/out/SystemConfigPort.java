package com.securityspring.core.ports.out;

import com.securityspring.core.domain.model.config.SystemConfig;

import java.util.List;
import java.util.Optional;

public interface SystemConfigPort {
    Optional<SystemConfig> findByKey(String key);
    List<SystemConfig> findAll();
    SystemConfig save(SystemConfig config);
    boolean getBoolean(String key, boolean defaultValue);
}
