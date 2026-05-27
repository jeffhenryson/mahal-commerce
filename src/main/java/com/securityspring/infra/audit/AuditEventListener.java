package com.securityspring.infra.audit;

import com.securityspring.core.domain.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    // Override this bean in downstream projects to persist events, send to Kafka, etc.
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        log.info("audit type={} username={} details={}", event.type(), event.username(), event.details());
    }
}
