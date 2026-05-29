package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.AuditLogEntity;
import com.securityspring.core.domain.event.AuditEvent;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.out.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepo;

    public AuditLogRepositoryImpl(AuditLogJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEvent event, String ipAddress) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUsername(event.username());
        entity.setAction(event.type().name());
        entity.setTarget(resolveTarget(event));
        entity.setDetails(event.details().isEmpty() ? null : event.details().toString());
        entity.setIpAddress(ipAddress);
        entity.setTimestamp(event.timestamp());
        jpaRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLogEntry> findFiltered(String username, String action, int page, int size) {
        Page<AuditLogEntity> p = jpaRepo.findFiltered(username, action, PageRequest.of(page, size));
        List<AuditLogEntry> content = p.getContent().stream()
                .map(e -> new AuditLogEntry(e.getId(), e.getUsername(), e.getAction(),
                        e.getTarget(), e.getDetails(), e.getIpAddress(), e.getTimestamp()))
                .collect(Collectors.toList());
        return new PageResult<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    private String resolveTarget(AuditEvent event) {
        Object role = event.details().get("role");
        if (role != null) return "role:" + role;
        return null;
    }
}
