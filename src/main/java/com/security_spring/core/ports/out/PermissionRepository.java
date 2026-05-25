package com.security_spring.core.ports.out;

import com.security_spring.core.domain.model.Permission;

import java.util.Optional;

public interface PermissionRepository {
    Permission save(Permission permission);
    Optional<Permission> findByName(String name);
}
