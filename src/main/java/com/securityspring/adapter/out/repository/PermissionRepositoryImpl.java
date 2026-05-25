package com.security_spring.adapter.out.repository;

import com.security_spring.adapter.out.entities.PermissionEntity;
import com.security_spring.core.domain.model.Permission;
import com.security_spring.core.ports.out.PermissionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class PermissionRepositoryImpl implements PermissionRepository {

    private final PermissionJpaRepository permRepo;

    public PermissionRepositoryImpl(PermissionJpaRepository permRepo) {
        this.permRepo = permRepo;
    }

    @Override
    public Permission save(Permission permission) {
        PermissionEntity entity = new PermissionEntity();
        entity.setId(permission.getId());
        entity.setName(permission.getName());
        PermissionEntity saved = permRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Permission> findByName(String name) {
        return permRepo.findByName(name).map(this::toDomain);
    }

    private Permission toDomain(PermissionEntity e) {
        Permission p = new Permission();
        p.setId(e.getId());
        p.setName(e.getName());
        return p;
    }
}
