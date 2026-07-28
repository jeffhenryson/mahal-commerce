package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashMovementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CashMovementJpaRepository extends JpaRepository<CashMovementEntity, Long> {

    List<CashMovementEntity> findBySessionIdOrderByIdAsc(Long sessionId);

    /**
     * Efeito líquido na gaveta. O {@code CASE} aplica o sinal do tipo — a coluna guarda sempre
     * valor positivo, e somá-la crua diria que uma sangria aumentou o caixa.
     *
     * <p>{@code COALESCE} porque uma sessão sem movimento algum é o caso normal, e devolver
     * {@code null} obrigaria todo chamador a tratá-lo.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(CASE WHEN m.type = 'SANGRIA' THEN -m.amount ELSE m.amount END), 0)
            FROM CashMovementEntity m
            WHERE m.sessionId = :sessionId
            """)
    BigDecimal sumSignedAmountBySessionId(@Param("sessionId") Long sessionId);
}
