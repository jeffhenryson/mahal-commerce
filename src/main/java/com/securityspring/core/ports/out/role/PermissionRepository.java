package com.securityspring.core.ports.out.role;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Permission;

import java.util.Optional;

public interface PermissionRepository {
    Permission save(Permission permission);
    Optional<Permission> findByName(String name);
    PageResult<Permission> findAll(int page, int size);
    void deleteByName(String name);
}
