package com.securityspring.adapter.in.dtos.response;

import com.securityspring.core.domain.model.AuditLogEntry;
import java.time.Instant;

public record AuditLogResponseDTO(
    Long id,
    String who,
    String action,
    String target,
    String ipAddress,
    Instant timestamp
) {
    public static AuditLogResponseDTO from(AuditLogEntry e) {
        return new AuditLogResponseDTO(e.id(), e.username(), e.action(), e.target(), e.ipAddress(), e.timestamp());
    }
}
