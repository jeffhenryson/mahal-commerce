package com.securityspring.adapter.out.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.securityspring.adapter.out.entities.RoleEntity;

import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);
}
