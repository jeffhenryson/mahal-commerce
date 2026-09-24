package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SessionAssetTypeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionAssetTypeJpaRepository extends JpaRepository<SessionAssetTypeEntity, Long> {

    List<SessionAssetTypeEntity> findAllByOrderByIdAsc();

    Optional<SessionAssetTypeEntity> findByCodigo(String codigo);

    /**
     * Trava os tipos que a sessão vai alocar, sempre em ordem de id — duas sessões disputando o
     * último vaso serializam aqui, e a ordem canônica evita deadlock entre elas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SessionAssetTypeEntity t WHERE t.id IN :ids ORDER BY t.id")
    List<SessionAssetTypeEntity> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
