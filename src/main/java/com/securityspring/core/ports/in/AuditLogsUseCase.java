package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;

public interface AuditLogsUseCase {
    PageResult<AuditLogEntry> list(String username, String action, int page, int size);
}
