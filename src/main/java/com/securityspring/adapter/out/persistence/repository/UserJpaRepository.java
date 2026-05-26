package com.securityspring.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.securityspring.adapter.out.persistence.entity.UserEntity;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.username = :username")
    Optional<UserEntity> findByUsernameWithRoles(@Param("username") String username);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.id = :id")
    Optional<UserEntity> findByIdWithRoles(@Param("id") Long id);

    @Query("select u.id from UserEntity u order by u.id")
    Page<Long> findAllIds(Pageable pageable);

    @Query("select distinct u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.id in :ids order by u.id")
    List<UserEntity> findAllWithRolesByIdIn(@Param("ids") List<Long> ids);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.email = :email")
    Optional<UserEntity> findByEmailWithRoles(@Param("email") String email);
}
