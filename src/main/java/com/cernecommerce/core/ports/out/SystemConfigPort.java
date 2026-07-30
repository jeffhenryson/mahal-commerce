package com.cernecommerce.core.ports.out;

import com.cernecommerce.core.domain.model.config.SystemConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SystemConfigPort {
    Optional<SystemConfig> findByKey(String key);
    List<SystemConfig> findAll();
    SystemConfig save(SystemConfig config);
    boolean getBoolean(String key, boolean defaultValue);
    int getInt(String key, int defaultValue);
    BigDecimal getDecimal(String key, BigDecimal defaultValue);
}
