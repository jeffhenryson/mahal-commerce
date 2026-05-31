package com.securityspring.adapter.out.persistence.repository;


import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.securityspring.adapter.out.persistence.entity.PermissionEntity;
import com.securityspring.adapter.out.persistence.entity.RoleEntity;
import com.securityspring.core.domain.exception.PermissionNotFoundException;
import com.securityspring.core.domain.exception.rbac.RoleNotFoundException;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.domain.model.rbac.Permission;
import com.securityspring.core.domain.model.rbac.Role;
import com.securityspring.core.ports.out.role.RoleRepository;

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
        Page<Long> idPage = roleRepo.findAllIds(PageRequest.of(page, size));
        List<Role> content = loadByIds(idPage.getContent());
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    @Override
    public void addPermissions(String roleName, Set<String> permissionNames) {
        RoleEntity role = roleRepo.findByName(roleName)
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        for (String name : permissionNames) {
            PermissionEntity perm = permRepo.findByName(name)
                    .orElseThrow(() -> new PermissionNotFoundException(name));
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
                .orElseThrow(() -> new RoleNotFoundException(roleName));
        role.getPermissions().removeIf(p -> p.getName().equals(permissionName));
        roleRepo.save(role);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Role> findByNameContaining(String search, int page, int size) {
        Page<Long> idPage = roleRepo.findIdsByNameContaining(search, PageRequest.of(page, size));
        List<Role> content = loadByIds(idPage.getContent());
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    private List<Role> loadByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, RoleEntity> byId = roleRepo.findAllWithPermissionsByIdIn(ids)
                .stream().collect(Collectors.toMap(RoleEntity::getId, r -> r));
        return ids.stream().filter(byId::containsKey).map(id -> toDomain(byId.get(id))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return roleRepo.count();
    }
}