package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TagJpaRepository extends JpaRepository<TagEntity, Long> {

    Optional<TagEntity> findByNome(String nome);

    @Query("SELECT t.id, t.nome, COUNT(ct) FROM TagEntity t "
            + "LEFT JOIN CustomerTagEntity ct ON ct.tagId = t.id "
            + "GROUP BY t.id, t.nome ORDER BY t.nome")
    List<Object[]> findAllWithCustomerCount();
}
