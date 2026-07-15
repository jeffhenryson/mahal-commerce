package com.cernecommerce.core.ports.in;

import java.util.Map;

public interface SystemConfigUseCase {
    Map<String, String> getAllPublic();
    Map<String, String> getAll();
    void set(String key, String value, String updatedBy);
    boolean getBoolean(String key, boolean defaultValue);
}
