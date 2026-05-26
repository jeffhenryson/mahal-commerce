package com.securityspring.adapter.out.repository;


import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.securityspring.adapter.out.entities.PermissionEntity;
import com.securityspring.adapter.out.entities.RoleEntity;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.Permission;
import com.securityspring.core.domain.model.Role;
import com.securityspring.core.ports.out.RoleRepository;

import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleJpaRepository roleRepo;
    private final PermissionJpaRepository permRepo;

    public RoleRepositoryImpl(RoleJpaRepository roleRepo, PermissionJpaRepository permRepo) {
        this.roleRepo = roleRepo;
        this.permRepo = permRepo;
    }

    private Role toDomain(RoleEntity e) {
        if (e == null) return null;
        java.util.Set<Permission> permissions = new java.util.HashSet<>();
        if (e.getPermissions() != null) {
            e.getPermissions().forEach(pe -> permissions.add(Permission.of(pe.getId(), pe.getName())));
        }
        return Role.of(e.getId(), e.getName(), permissions);
    }

    @Override
    public Role save(Role role) {
        RoleEntity entity = new RoleEntity(); // evita exigir id no construtor
        entity.setId(role.getId());           // seta se vier preenchido
        entity.setName(role.getName());

        RoleEntity saved = roleRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findByName(String name) {
        return roleRepo.findByName(name).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Role> findById(Long id) {
        return roleRepo.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Role> findAll(int page, int size) {
        Page<RoleEntity> p = roleRepo.findAll(PageRequest.of(page, size));
        List<Role> content = p.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public void addPermissions(String roleName, Set<String> permissionNames) {
        RoleEntity role = roleRepo.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        for (String name : permissionNames) {
            PermissionEntity perm = permRepo.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + name));
            role.getPermissions().add(perm);
        }
        roleRepo.save(role);
    }

    @Override
    public void deleteByName(String name) {
        roleRepo.findByName(name).ifPresent(roleRepo::delete);
    }

    @Override
    public void removePermission(String roleName, String permissionName) {
        RoleEntity role = roleRepo.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        role.getPermissions().removeIf(p -> p.getName().equals(permissionName));
        roleRepo.save(role);
    }
}