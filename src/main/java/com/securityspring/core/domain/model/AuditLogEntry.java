package com.securityspring.core.domain.model;

import java.time.Instant;

public record AuditLogEntry(
    Long id,
    String username,
    String action,
    String target,
    String details,
    String ipAddress,
    Instant timestamp
) {}
