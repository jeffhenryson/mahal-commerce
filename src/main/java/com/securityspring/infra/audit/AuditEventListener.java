package com.securityspring.infra.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.ports.out.audit.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditLogRepository auditLogRepository;

    public AuditEventListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        log.info("audit type={} username={} details={}", event.type(), event.username(), event.details());
        auditLogRepository.save(event, resolveIp());
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
