package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long>,
        JpaSpecificationExecutor<OrderEntity> {

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

    // findFiltered virou Specification (ver OrderRepositoryImpl.findAll): o padrão
    // "(:from IS NULL OR o.createdAt >= :from)" fazia o Postgres real recusar inferir o tipo do
    // bind quando from/to (Instant) vinham nulos ("could not determine data type of parameter").
    // A correção óbvia — CAST(:from AS timestamp) — trocou esse erro por outro: o Hibernate passou
    // a mandar o parâmetro como bytea para o driver ("cannot cast type bytea to timestamp without
    // time zone"), um problema conhecido de CAST sobre parâmetro nomeado em JPQL. Specification
    // elimina a classe inteira do problema: quando o filtro é nulo, o predicado simplesmente não
    // é adicionado, sem bind ambíguo de tipo em lugar nenhum.

    // ── Agregação de GET /orders/summary ────────────────────────────────────────────────────
    // from/to são obrigatórios neste endpoint (validados em OrderReportService) — nunca chegam
    // nulos aqui, então a comparação direta (sem CAST) nunca teve o problema de "could not
    // determine data type". Um CAST(:from AS timestamp) tinha sido colocado por consistência com
    // findFiltered, mas isso reintroduziu o bug do bytea (ver nota acima) mesmo sem nulo
    // envolvido — o problema é do CAST sobre parâmetro em si, não da nulidade.

    /** Distribuição completa por status — sem filtro de receita, é a contagem de tudo no período. */
    @Query("""
            SELECT o.status AS status, COUNT(o) AS count
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
            GROUP BY o.status
            """)
    List<StatusCountProjection> countByStatus(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Totais de receita — só status com pagamento confirmado, exceto reembolsado
     * ({@code CANCELADO}/{@code REEMBOLSADO} não zeram os totais do pedido, e contá-los aqui
     * infla a receita com dinheiro que nunca entrou ou que voltou).
     */
    @Query("""
            SELECT COUNT(o) AS orderCount, COALESCE(SUM(o.netAmount), 0) AS totalRevenueNet,
                   COALESCE(SUM(o.totalAmount), 0) AS totalRevenueGross
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            """)
    HeaderTotalsProjection findRevenueTotals(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT o.channel AS channel, COALESCE(SUM(o.netAmount), 0) AS revenue
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            GROUP BY o.channel
            """)
    List<ChannelRevenueProjection> findRevenueByChannel(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            SELECT CAST(o.createdAt AS date) AS day, COALESCE(SUM(o.netAmount), 0) AS revenue,
                   COUNT(o) AS orderCount
            FROM OrderEntity o
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO','RESERVADO')
            GROUP BY CAST(o.createdAt AS date)
            ORDER BY CAST(o.createdAt AS date) ASC
            """)
    List<DailyRevenueProjection> findDailyRevenue(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to);

    interface StatusCountProjection {
        String getStatus();
        long getCount();
    }

    interface HeaderTotalsProjection {
        long getOrderCount();
        BigDecimal getTotalRevenueNet();
        BigDecimal getTotalRevenueGross();
    }

    interface ChannelRevenueProjection {
        String getChannel();
        BigDecimal getRevenue();
    }

    interface DailyRevenueProjection {
        java.time.LocalDate getDay();
        BigDecimal getRevenue();
        long getOrderCount();
    }
}
