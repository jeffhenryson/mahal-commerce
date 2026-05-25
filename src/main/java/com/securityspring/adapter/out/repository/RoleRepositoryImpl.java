package com.securityspring.adapter.out.repository;


import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.securityspring.adapter.out.entities.PermissionEntity;
import com.securityspring.adapter.out.entities.RoleEntity;
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
        Role r = new Role();
        r.setId(e.getId());
        r.setName(e.getName());
        if (e.getPermissions() != null) {
            e.getPermissions().forEach(pe -> {
                com.securityspring.core.domain.model.Permission p = new com.securityspring.core.domain.model.Permission();
                p.setId(pe.getId());
                p.setName(pe.getName());
                r.addPermission(p);
            });
        }
        return r;
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
    public Optional<Role> findByName(String name) {
        return roleRepo.findByName(name).map(this::toDomain);
    }

    @Override
    public Optional<Role> findById(Long id) {
        return roleRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Role> findAll() {
        return roleRepo.findAll().stream().map(this::toDomain).toList();
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