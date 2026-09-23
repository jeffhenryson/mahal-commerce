package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.KitTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KitTemplateJpaRepository extends JpaRepository<KitTemplateEntity, Long> {

    @Query("SELECT DISTINCT t FROM KitTemplateEntity t LEFT JOIN FETCH t.steps ORDER BY t.name")
    List<KitTemplateEntity> findAllWithSteps();

    @Query("SELECT t FROM KitTemplateEntity t LEFT JOIN FETCH t.steps WHERE t.id = :id")
    Optional<KitTemplateEntity> findByIdWithSteps(@Param("id") Long id);

    @Query("SELECT t FROM KitTemplateEntity t WHERE LOWER(t.name) = LOWER(:name)")
    Optional<KitTemplateEntity> findByNameIgnoringCase(@Param("name") String name);
}
