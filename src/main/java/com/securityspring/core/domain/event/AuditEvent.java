package com.securityspring.core.domain.event;

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
        USER_LOGGED_IN, USER_LOGGED_OUT, USER_SESSIONS_CLEARED,
        USER_CREATED, USER_DELETED, USER_UPDATED, USER_EMAIL_CHANGED,
        USER_ROLE_ASSIGNED, USER_ENABLED, USER_DISABLED,
        USER_PASSWORD_CHANGED,
        PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED,
        EMAIL_CHANGE_REQUESTED, EMAIL_CHANGE_CONFIRMED
    }

    public static AuditEvent of(EventType type, String username) {
        return new AuditEvent(type, username, Instant.now(), Map.of());
    }

    public static AuditEvent of(EventType type, String username, Map<String, Object> details) {
        return new AuditEvent(type, username, Instant.now(), Map.copyOf(details));
    }
}
