package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.PermissionNotFoundException;
import com.cernecommerce.core.domain.exception.RoleAlreadyExistsException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.out.role.PermissionRepository;
import com.cernecommerce.core.ports.out.role.RoleRepository;
import com.cernecommerce.core.ports.out.user.UserCachePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

public class RoleService implements RoleUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserCachePort userCachePort;

    public RoleService(RoleRepository roleRepository, PermissionRepository permissionRepository,
            UserRepository userRepository, UserCachePort userCachePort) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.userCachePort = userCachePort;
    }

    @Override
    @Transactional
    public Role createRole(String name) {
        roleRepository.findByName(name).ifPresent(r -> {
            throw new RoleAlreadyExistsException(name);
        });
        Role role = new Role(name);
        return roleRepository.save(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Role> listAll(int page, int size) {
        return roleRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Role findByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException(name));
    }

    @Override
    @Transactional
    public void deleteRole(String name) {
        roleRepository.findByName(name)
                .orElseThrow(() -> new RoleNotFoundException(name));
        roleRepository.deleteByName(name);
    }

    @Override
    @Transactional
    public void assignPermission(String roleName, String permissionName) {
        roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new PermissionNotFoundException(permissionName));
        roleRepository.addPermissions(roleName, java.util.Set.of(permissionName));
        evictUsersWithRole(roleName);
    }

    @Override
    @Transactional
    public void removePermission(String roleName, String permissionName) {
        roleRepository.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        roleRepository.removePermission(roleName, permissionName);
        evictUsersWithRole(roleName);
    }

    /**
     * Evicta o cache de authorities de todo usuário com a role alterada — sem isso, usuários
     * mantêm a authority antiga até o TTL do cache expirar, mesmo em resposta a um incidente
     * de segurança que exija revogação imediata (ver C003).
     */
    private void evictUsersWithRole(String roleName) {
        userRepository.findUsernamesByRole(roleName).forEach(userCachePort::evict);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Role> findByNameContaining(String search, int page, int size) {
        return roleRepository.findByNameContaining(search, page, size);
    }
}
