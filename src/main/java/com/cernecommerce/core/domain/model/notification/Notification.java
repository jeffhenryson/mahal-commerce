package com.cernecommerce.core.domain.model.notification;

import java.time.Instant;

public record Notification(
    Long id,
    String username,
    NotificationType type,
    String title,
    String body,
    String targetUrl,
    Instant readAt,
    Instant createdAt
) {
    /** Compatibilidade com chamadores anteriores ao {@code targetUrl} — nasce sem vínculo. */
    public Notification(Long id, String username, NotificationType type, String title, String body,
            Instant readAt, Instant createdAt) {
        this(id, username, type, title, body, null, readAt, createdAt);
    }

    public boolean isRead() {
        return readAt != null;
    }
}
