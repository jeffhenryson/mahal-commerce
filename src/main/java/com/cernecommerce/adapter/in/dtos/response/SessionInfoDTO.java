package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.auth.SessionInfo;

import java.time.Instant;

public record SessionInfoDTO(Long id, Instant createdAt, Instant expiresAt, String ipAddress, String userAgent) {
    public static SessionInfoDTO from(SessionInfo s) {
        return new SessionInfoDTO(s.id(), s.createdAt(), s.expiresAt(), s.ipAddress(), s.userAgent());
    }
}
