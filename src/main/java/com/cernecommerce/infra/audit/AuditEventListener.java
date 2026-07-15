package com.cernecommerce.infra.audit;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.ports.out.SystemConfigPort;
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
    private final SystemConfigPort systemConfig;

    public AuditEventListener(AuditPersistenceService persistenceService, SystemConfigPort systemConfig) {
        this.persistenceService = persistenceService;
        this.systemConfig = systemConfig;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        log.info("audit type={} username={} details={}", event.type(), event.username(), event.details());
        if (!systemConfig.getBoolean("module.audit-logs.enabled", true)) {
            return;
        }
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
