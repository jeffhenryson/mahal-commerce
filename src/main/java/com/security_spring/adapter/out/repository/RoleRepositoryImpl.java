package com.security_spring.adapter.out.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.security_spring.adapter.out.entities.RoleEntity;
import com.security_spring.core.domain.model.Role;
import com.security_spring.core.ports.out.RoleRepository;

import jakarta.transaction.Transactional;

@Repository
@Transactional
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleJpaRepository roleRepo;

    public RoleRepositoryImpl(RoleJpaRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    private Role toDomain(RoleEntity e) {
        if (e == null) return null;
        Role r = new Role();
        r.setId(e.getId());
        r.setName(e.getName());
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
        // Se estiver em Java 8, use Collectors.toList()
    }
}