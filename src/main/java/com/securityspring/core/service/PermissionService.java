package com.securityspring.core.service;

import com.securityspring.core.domain.exception.PermissionAlreadyExistsException;
import com.securityspring.core.domain.exception.PermissionNotFoundException;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Permission;
import com.securityspring.core.ports.in.PermissionUseCase;
import com.securityspring.core.ports.out.PermissionRepository;

public class PermissionService implements PermissionUseCase {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    public Permission createPermission(String name) {
        permissionRepository.findByName(name).ifPresent(p -> {
            throw new PermissionAlreadyExistsException(name);
        });
        Permission permission = new Permission(name);
        return permissionRepository.save(permission);
    }

    @Override
    public PageResult<Permission> listAll(int page, int size) {
        return permissionRepository.findAll(page, size);
    }

    @Override
    public Permission findByName(String name) {
        return permissionRepository.findByName(name)
                .orElseThrow(() -> new PermissionNotFoundException(name));
    }

    @Override
    public void deletePermission(String name) {
        permissionRepository.findByName(name)
                .orElseThrow(() -> new PermissionNotFoundException(name));
        permissionRepository.deleteByName(name);
    }
}
