package com.securityspring.core.service;

import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.in.AuditLogsUseCase;
import com.securityspring.core.ports.out.audit.AuditLogRepository;

public class AuditLogsService implements AuditLogsUseCase {

    private final AuditLogRepository repository;

    public AuditLogsService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public PageResult<AuditLogEntry> list(String username, String action, int page, int size) {
        return repository.findFiltered(username, action, page, size);
    }
}
