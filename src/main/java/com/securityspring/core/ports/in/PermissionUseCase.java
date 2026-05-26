package com.securityspring.core.ports.in;

import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Permission;

public interface PermissionUseCase {
    Permission createPermission(String name);
    PageResult<Permission> listAll(int page, int size);
    Permission findByName(String name);
    void deletePermission(String name);
}
