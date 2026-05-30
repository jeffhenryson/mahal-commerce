package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;

import java.time.Instant;

public interface AuditLogsUseCase {
    PageResult<AuditLogEntry> list(String username, String action, Instant from, Instant to, int page, int size);
}
