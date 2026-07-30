package com.cernecommerce.core.ports.in;

import java.math.BigDecimal;
import java.util.Map;

public interface SystemConfigUseCase {
    Map<String, String> getAllPublic();
    Map<String, String> getAll();
    void set(String key, String value, String updatedBy);
    boolean getBoolean(String key, boolean defaultValue);
    int getInt(String key, int defaultValue);
    BigDecimal getDecimal(String key, BigDecimal defaultValue);
}
