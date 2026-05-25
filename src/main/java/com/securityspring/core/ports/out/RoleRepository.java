package com.securityspring.core.ports.out;

import com.securityspring.core.domain.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleRepository {
    Role save(Role role);

    Optional<Role> findByName(String name);

    Optional<Role> findById(Long id);

    List<Role> findAll();

    void addPermissions(String roleName, Set<String> permissionNames);

    void deleteByName(String name);

    void removePermission(String roleName, String permissionName);
}