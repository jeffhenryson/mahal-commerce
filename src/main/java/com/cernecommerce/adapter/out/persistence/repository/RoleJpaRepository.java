package com.cernecommerce.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cernecommerce.adapter.out.persistence.entity.RoleEntity;

import java.util.List;
import java.util.Optional;

public interface RoleJpaRepository extends JpaRepository<RoleEntity, Long> {

    @Query("select r from RoleEntity r left join fetch r.permissions where r.name = :name")
    Optional<RoleEntity> findByName(@Param("name") String name);

    // ID-first pagination: conta sem fetch, depois carrega com JOIN FETCH — evita N+1.
    @Query("select r.id from RoleEntity r order by r.id")
    Page<Long> findAllIds(Pageable pageable);

    @Query("select distinct r from RoleEntity r left join fetch r.permissions where r.id in :ids order by r.id")
    List<RoleEntity> findAllWithPermissionsByIdIn(@Param("ids") List<Long> ids);

    @Query("select r.id from RoleEntity r where lower(r.name) like lower(concat('%', :search, '%')) order by r.name")
    Page<Long> findIdsByNameContaining(@Param("search") String search, Pageable pageable);
}
