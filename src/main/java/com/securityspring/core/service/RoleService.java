package com.securityspring.core.service;

import com.securityspring.core.domain.exception.PermissionNotFoundException;
import com.securityspring.core.domain.exception.RoleAlreadyExistsException;
import com.securityspring.core.domain.exception.rbac.RoleNotFoundException;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.ports.in.RoleUseCase;
import com.securityspring.core.ports.out.role.PermissionRepository;
import com.securityspring.core.ports.out.role.RoleRepository;

public class RoleService implements RoleUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public Role createRole(String name) {
        roleRepository.findByName(name).ifPresent(r -> {
            throw new RoleAlreadyExistsException(name);
        });
        Role role = new Role(name);
        return roleRepository.save(role);
    }

    @Override
    public PageResult<Role> listAll(int page, int size) {
        return roleRepository.findAll(page, size);
    }

    @Override
    public Role findByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException(name));
    }

    @Override
    public void deleteRole(String name) {
        roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException(name));
        roleRepository.deleteByName(name);
    }

    @Override
    public void assignPermission(String roleName, String permissionName) {
        roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new PermissionNotFoundException(permissionName));
        roleRepository.addPermissions(roleName, java.util.Set.of(permissionName));
    }

    @Override
    public void removePermission(String roleName, String permissionName) {
        roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        roleRepository.removePermission(roleName, permissionName);
    }

    @Override
    public PageResult<Role> findByNameContaining(String search, int page, int size) {
        return roleRepository.findByNameContaining(search, page, size);
    }
}
