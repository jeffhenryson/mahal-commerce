package com.securityspring.infra.audit;

import com.securityspring.core.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditPersistenceService persistenceService;

    public AuditEventListener(AuditPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        log.info("audit type={} username={} details={}", event.type(), event.username(), event.details());
        // IP deve ser resolvido na thread do request — antes de passar para execução assíncrona.
        String ip = resolveIp();
        persistenceService.saveAsync(event, ip);
    }

    private String resolveIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest().getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
