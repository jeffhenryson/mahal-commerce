package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.Role;

import java.util.List;

public interface RoleUseCase {
    Role createRole(String name);
    List<Role> listAll();
    Role findByName(String name);
    void deleteRole(String name);
    void assignPermission(String roleName, String permissionName);
    void removePermission(String roleName, String permissionName);
}
