package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.AttributeTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttributeTypeJpaRepository extends JpaRepository<AttributeTypeEntity, Long> {

    @Query("SELECT t FROM AttributeTypeEntity t WHERE LOWER(t.name) = LOWER(:name)")
    Optional<AttributeTypeEntity> findByNameIgnoringCase(@Param("name") String name);

    List<AttributeTypeEntity> findAllByOrderByNameAsc();
}
