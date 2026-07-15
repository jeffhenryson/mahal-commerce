package com.cernecommerce.core.ports.out.role;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Role;

import java.util.Optional;
import java.util.Set;

public interface RoleRepository {
    Role save(Role role);

    Optional<Role> findByName(String name);

    Optional<Role> findById(Long id);

    PageResult<Role> findAll(int page, int size);

    void addPermissions(String roleName, Set<String> permissionNames);

    void deleteByName(String name);

    void removePermission(String roleName, String permissionName);

    PageResult<Role> findByNameContaining(String search, int page, int size);

    long countAll();
}