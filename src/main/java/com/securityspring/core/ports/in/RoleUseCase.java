package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Role;

public interface RoleUseCase {
    Role createRole(String name);
    PageResult<Role> listAll(int page, int size);
    Role findByName(String name);
    void deleteRole(String name);
    void assignPermission(String roleName, String permissionName);
    void removePermission(String roleName, String permissionName);
}
