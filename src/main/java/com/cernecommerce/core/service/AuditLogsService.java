package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.ports.in.AuditLogsUseCase;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

public class AuditLogsService implements AuditLogsUseCase {

    private final AuditLogRepository repository;

    public AuditLogsService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLogEntry> list(String username, String action, Instant from, Instant to, int page, int size, Set<String> excludeActions) {
        return repository.findFiltered(username, action, from, to, page, size, excludeActions);
    }
}
