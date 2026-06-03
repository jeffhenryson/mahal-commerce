package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.SystemConfigEntity;
import com.securityspring.core.domain.model.config.SystemConfig;
import com.securityspring.core.ports.out.SystemConfigPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
public class SystemConfigAdapter implements SystemConfigPort {

    private final SystemConfigJpaRepository jpa;

    public SystemConfigAdapter(SystemConfigJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SystemConfig> findByKey(String key) {
        return jpa.findById(key).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfig> findAll() {
        return jpa.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    @Override
    public SystemConfig save(SystemConfig config) {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setKey(config.key());
        entity.setValue(config.value());
        entity.setUpdatedAt(config.updatedAt());
        entity.setUpdatedBy(config.updatedBy());
        return toDomain(jpa.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return jpa.findById(key)
            .map(e -> Boolean.parseBoolean(e.getValue()))
            .orElse(defaultValue);
    }

    private SystemConfig toDomain(SystemConfigEntity e) {
        return new SystemConfig(e.getKey(), e.getValue(), e.getUpdatedAt(), e.getUpdatedBy());
    }
}
