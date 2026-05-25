package com.securityspring.core.ports.out;

import com.securityspring.core.domain.model.Permission;

import java.util.Optional;

import java.util.List;

public interface PermissionRepository {
    Permission save(Permission permission);
    Optional<Permission> findByName(String name);
    List<Permission> findAll();
    void deleteByName(String name);
}
