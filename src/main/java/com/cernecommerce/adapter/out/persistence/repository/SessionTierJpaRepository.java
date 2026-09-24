package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SessionTierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionTierJpaRepository extends JpaRepository<SessionTierEntity, Long> {

    List<SessionTierEntity> findAllByOrderByOrdemAscIdAsc();

    Optional<SessionTierEntity> findFirstByNomeIgnoreCase(String nome);
}
