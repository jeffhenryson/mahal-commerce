package com.cernecommerce.core.domain.model.notification;

public enum NotificationType {
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED,
    TOTP_ENABLED,
    TOTP_DISABLED,
    TOKEN_THEFT_DETECTED,
    EMAIL_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    ACCOUNT_DISABLED,
    SYSTEM
}
