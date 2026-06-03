package com.securityspring.core.service;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.in.AuditLogsUseCase;
import com.securityspring.core.ports.out.audit.AuditLogRepository;

import java.time.Instant;
import java.util.Set;

public class AuditLogsService implements AuditLogsUseCase {

    private final AuditLogRepository repository;

    public AuditLogsService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<AuditLogEntry> list(String username, String action, Instant from, Instant to, int page, int size, Set<String> excludeActions) {
        return repository.findFiltered(username, action, from, to, page, size, excludeActions);
    }
}
