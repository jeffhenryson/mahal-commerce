package com.securityspring.adapter.out.persistence.repository;

import com.securityspring.adapter.out.persistence.entity.PermissionEntity;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Permission;
import com.securityspring.core.ports.out.role.PermissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public PageResult<Permission> findAll(int page, int size) {
        Page<PermissionEntity> p = permRepo.findAll(PageRequest.of(page, size));
        List<Permission> content = p.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public void deleteByName(String name) {
        permRepo.findByName(name).ifPresent(permRepo::delete);
    }

    private Permission toDomain(PermissionEntity e) {
        return Permission.of(e.getId(), e.getName());
    }
}
