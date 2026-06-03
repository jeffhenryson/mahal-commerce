package com.securityspring.core.domain.model.config;

import java.time.Instant;

public record SystemConfig(String key, String value, Instant updatedAt, String updatedBy) {

    public boolean asBoolean() {
        return Boolean.parseBoolean(value);
    }
}
