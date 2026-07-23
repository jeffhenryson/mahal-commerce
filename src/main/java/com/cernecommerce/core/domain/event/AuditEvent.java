package com.cernecommerce.core.domain.event;

import java.time.Instant;
import java.util.Map;

/**
 * Evento de auditoria do domínio.
 *
 * <p>Publicado via {@link org.springframework.context.ApplicationEventPublisher} pelos controllers
 * (adapter/in) e consumido por {@code AuditEventListener} na camada de infra.
 * Por ser um record de domínio sem dependências de framework, pode ser publicado
 * por qualquer camada sem quebrar o isolamento da arquitetura hexagonal.</p>
 */
public record AuditEvent(EventType type, String username, Instant timestamp, Map<String, Object> details) {

    public enum EventType {
        // Auth
        USER_LOGGED_IN, USER_LOGGED_OUT, USER_SESSIONS_CLEARED,
        LOGIN_FAILED, ACCOUNT_LOCKED, TOKEN_THEFT_DETECTED, ACCESS_DENIED,
        // User lifecycle
        USER_REGISTERED, USER_EMAIL_VERIFIED,
        USER_CREATED, USER_DELETED, USER_UPDATED, USER_EMAIL_CHANGED,
        USER_ROLE_ASSIGNED, USER_ROLE_REMOVED, USER_ENABLED, USER_DISABLED,
        USER_PASSWORD_CHANGED,
        // Password reset
        PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED,
        // Email change
        EMAIL_CHANGE_REQUESTED, EMAIL_CHANGE_CONFIRMED,
        // RBAC management
        ROLE_CREATED, ROLE_DELETED,
        PERMISSION_CREATED, PERMISSION_DELETED,
        PERMISSION_ASSIGNED_TO_ROLE, PERMISSION_REMOVED_FROM_ROLE,
        // 2FA
        TOTP_ENABLED, TOTP_DISABLED, TOTP_BACKUP_CODES_REGENERATED, TOTP_REPLACED,
        // DEV elevation
        DEV_ELEVATION_COMPLETED,
        // OAuth
        OAUTH_GOOGLE_LOGIN,
        // Estoque
        PRODUCT_CREATED, WAREHOUSE_CREATED, STOCK_MOVEMENT_REGISTERED, REORDER_POINT_SET,
        // CRM
        CUSTOMER_CREATED, CUSTOMER_NOTE_ADDED, CUSTOMER_STAGE_CHANGED,
        TAG_CREATED, TAG_DELETED, CUSTOMER_TAG_ADDED, CUSTOMER_TAG_REMOVED,
        CAMPAIGN_AUTOMATION_CREATED, CAMPAIGN_AUTOMATION_DELETED, CAMPAIGN_AUTOMATION_DISPATCHED
    }

    public static AuditEvent of(EventType type, String username) {
        return new AuditEvent(type, username, Instant.now(), Map.of());
    }

    public static AuditEvent of(EventType type, String username, Map<String, Object> details) {
        return new AuditEvent(type, username, Instant.now(), Map.copyOf(details));
    }
}
