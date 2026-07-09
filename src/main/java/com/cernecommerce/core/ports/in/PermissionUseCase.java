package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Permission;

public interface PermissionUseCase {
    Permission createPermission(String name);
    PageResult<Permission> listAll(int page, int size);
    Permission findByName(String name);
    void deletePermission(String name);
}
