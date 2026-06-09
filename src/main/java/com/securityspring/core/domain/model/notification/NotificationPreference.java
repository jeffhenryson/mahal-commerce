package com.securityspring.core.domain.model.notification;

public record NotificationPreference(
        String username,
        NotificationType type,
        boolean inAppEnabled,
        boolean emailEnabled) {

    public static NotificationPreference defaultFor(String username, NotificationType type) {
        return new NotificationPreference(username, type, true, true);
    }
}
