package com.security_spring.adapter.out.repository;


import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;

import com.security_spring.adapter.out.entities.PermissionEntity;
import com.security_spring.adapter.out.entities.RoleEntity;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.ports.out.RoleRepository;

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
                com.security_spring.core.domain.model.Permission p = new com.security_spring.core.domain.model.Permission();
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
}