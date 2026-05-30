package com.securityspring.core.ports.out.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;

import java.time.Instant;

public interface AuditLogRepository {
    void save(AuditEvent event, String ipAddress);
    PageResult<AuditLogEntry> findFiltered(String username, String action, Instant from, Instant to, int page, int size);
    /** Remove entradas mais antigas que {@code cutoff}. Chamado pelo scheduler de retenção. */
    long deleteOlderThan(Instant cutoff);
}
