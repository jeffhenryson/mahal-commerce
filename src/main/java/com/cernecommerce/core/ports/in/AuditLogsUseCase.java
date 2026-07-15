package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;

import java.time.Instant;
import java.util.Set;

public interface AuditLogsUseCase {
    PageResult<AuditLogEntry> list(String username, String action, Instant from, Instant to, int page, int size, Set<String> excludeActions);
}
