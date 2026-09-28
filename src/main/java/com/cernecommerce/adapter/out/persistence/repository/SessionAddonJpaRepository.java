package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SessionAddonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionAddonJpaRepository extends JpaRepository<SessionAddonEntity, Long> {

    List<SessionAddonEntity> findAllByOrderByOrdemAscIdAsc();

    Optional<SessionAddonEntity> findFirstByNomeIgnoreCase(String nome);
}
