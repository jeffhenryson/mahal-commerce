package com.security_spring.adapter.out.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.security_spring.adapter.out.entities.UserEntity;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
}
