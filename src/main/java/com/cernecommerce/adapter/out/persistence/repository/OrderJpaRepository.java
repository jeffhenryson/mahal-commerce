package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    Page<OrderEntity> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    /**
     * Próximo número da sequência dedicada de numeração de pedido.
     *
     * <p>{@code nextval} é nativo porque a sequência não pertence a nenhuma entidade — ela existe
     * justamente para <b>não</b> derivar do {@code BIGSERIAL} do id, que deixa buracos em rollback.</p>
     */
    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long nextOrderNumber();

    /**
     * Receita concluída da sessão. Só {@code CONCLUIDO} entra: pedido cancelado não gerou dinheiro
     * na gaveta, e somá-lo faria o esperado do fechamento acusar uma falta que nunca existiu.
     */
    @Query("""
            SELECT COALESCE(SUM(o.netAmount), 0)
            FROM OrderEntity o
            WHERE o.sessionId = :sessionId AND o.status = 'CONCLUIDO'
            """)
    BigDecimal sumConcludedNetAmountBySessionId(@Param("sessionId") Long sessionId);

    /**
     * Listagem filtrada da visão do administrador. Cada filtro é opcional pelo padrão
     * {@code :param IS NULL OR ...} — uma Specification daria o mesmo resultado com muito mais
     * cerimônia para cinco critérios fixos.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND (:from       IS NULL OR o.createdAt >= :from)
              AND (:to         IS NULL OR o.createdAt <= :to)
            ORDER BY o.id DESC
            """)
    Page<OrderEntity> findFiltered(@Param("channel") String channel,
            @Param("status") String status,
            @Param("customerId") Long customerId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
