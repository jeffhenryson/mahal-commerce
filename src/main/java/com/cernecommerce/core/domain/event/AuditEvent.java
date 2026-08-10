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
        OAUTH_GOOGLE_LOGIN, OAUTH_GOOGLE_DISABLED_ATTEMPT,
        // Estoque
        PRODUCT_CREATED, WAREHOUSE_CREATED, STOCK_MOVEMENT_REGISTERED, REORDER_POINT_SET,
        PRODUCT_UPDATED, PRODUCT_ACTIVATED, PRODUCT_DEACTIVATED, PRODUCT_PRICE_CHANGED,
        WAREHOUSE_UPDATED, WAREHOUSE_ACTIVATED, WAREHOUSE_DEACTIVATED,
        STOCK_COUNT_OPENED, STOCK_COUNT_CLOSED, STOCK_COUNT_CANCELLED, KIT_RECIPE_CHANGED,
        PRODUCT_LOT_TRACKED_ENABLED, PRODUCT_LOT_TRACKED_DISABLED,
        PRODUCT_IMAGE_UPLOADED,
        // CRM
        CUSTOMER_CREATED, CUSTOMER_NOTE_ADDED, CUSTOMER_STAGE_CHANGED,
        // Ecommerce (ECM-F001, Fatia 8) — distinto de CUSTOMER_CREATED: nasce de autocadastro
        // público em /shop/register, não de um operador com CRM_CUSTOMER_MANAGE.
        CUSTOMER_MARKETPLACE_REGISTERED,
        TAG_CREATED, TAG_DELETED, CUSTOMER_TAG_ADDED, CUSTOMER_TAG_REMOVED,
        CAMPAIGN_AUTOMATION_CREATED, CAMPAIGN_AUTOMATION_DELETED, CAMPAIGN_AUTOMATION_DISPATCHED,
        CAMPAIGN_AUTOMATION_TOGGLED, CUSTOMER_LIST_EXPORTED,
        // PDV — ciclo de caixa
        CASH_SESSION_OPENED, CASH_SESSION_CLOSED, CASH_MOVEMENT_REGISTERED,
        // Pedido
        ORDER_STATUS_CHANGED, ORDER_CANCELLED, ORDER_REFUNDED,
        // Cashback (CRM-F003)
        CASHBACK_RATE_CHANGED, CASHBACK_EARNED
    }

    public static AuditEvent of(EventType type, String username) {
        return new AuditEvent(type, username, Instant.now(), Map.of());
    }

    public static AuditEvent of(EventType type, String username, Map<String, Object> details) {
        return new AuditEvent(type, username, Instant.now(), Map.copyOf(details));
    }
}
