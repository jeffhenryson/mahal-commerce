package com.cernecommerce.core.domain.model.notification;

import java.time.Instant;

public record Notification(
    Long id,
    String username,
    NotificationType type,
    String title,
    String body,
    Instant readAt,
    Instant createdAt
) {
    public boolean isRead() {
        return readAt != null;
    }
}
