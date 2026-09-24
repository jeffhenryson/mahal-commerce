package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaSessionAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ComandaSessionAssetJpaRepository extends JpaRepository<ComandaSessionAssetEntity, Long> {

    @Query("""
            SELECT a.assetTypeId, SUM(a.quantidade) FROM ComandaSessionAssetEntity a
            WHERE a.liberadoEm IS NULL GROUP BY a.assetTypeId
            """)
    List<Object[]> sumInUseByType();

    List<ComandaSessionAssetEntity> findByComandaItemIdInAndLiberadoEmIsNull(Collection<Long> itemIds);

    @Modifying
    @Query("""
            UPDATE ComandaSessionAssetEntity a SET a.liberadoEm = :at
            WHERE a.comandaItemId IN :itemIds AND a.liberadoEm IS NULL
            """)
    int releaseByItemIds(@Param("itemIds") Collection<Long> itemIds, @Param("at") Instant at);
}
