package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.Permission;

import java.util.List;

public interface PermissionUseCase {
    Permission createPermission(String name);
    List<Permission> listAll();
    Permission findByName(String name);
    void deletePermission(String name);
}
