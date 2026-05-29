package com.securityspring.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.securityspring.adapter.out.persistence.entity.RoleEntity;

import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {
    Optional<RoleEntity> findByName(String name);

    @Query("select r from RoleEntity r where lower(r.name) like lower(concat('%', :search, '%')) order by r.name")
    Page<RoleEntity> findByNameContaining(@Param("search") String search, Pageable pageable);
}
