package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.PermissionAlreadyExistsException;
import com.cernecommerce.core.domain.exception.PermissionNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import org.springframework.transaction.annotation.Transactional;

public class PermissionService implements PermissionUseCase {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public Permission createPermission(String name) {
        permissionRepository.findByName(name).ifPresent(p -> {
            throw new PermissionAlreadyExistsException(name);
        });
        Permission permission = new Permission(name);
        return permissionRepository.save(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Permission> listAll(int page, int size) {
        return permissionRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Permission findByName(String name) {
        return permissionRepository.findByName(name)
                .orElseThrow(() -> new PermissionNotFoundException(name));
    }

    @Override
    @Transactional
    public void deletePermission(String name) {
        permissionRepository.findByName(name)
                .orElseThrow(() -> new PermissionNotFoundException(name));
        permissionRepository.deleteByName(name);
    }
}
