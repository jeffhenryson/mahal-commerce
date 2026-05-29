package com.securityspring.core.ports.out.audit;

import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;

public interface AuditLogRepository {
    void save(AuditEvent event, String ipAddress);
    PageResult<AuditLogEntry> findFiltered(String username, String action, int page, int size);
}
