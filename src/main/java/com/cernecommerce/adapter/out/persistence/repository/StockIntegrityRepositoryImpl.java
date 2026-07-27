package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Repository
@Transactional(readOnly = true)
public class StockIntegrityRepositoryImpl implements StockIntegrityRepository {

    private final StockIntegrityJpaRepository stockIntegrityJpaRepository;

    public StockIntegrityRepositoryImpl(StockIntegrityJpaRepository stockIntegrityJpaRepository) {
        this.stockIntegrityJpaRepository = stockIntegrityJpaRepository;
    }

    @Override
    public PageResult<OrphanSku> findOrphanSkus(int page, int size) {
        Page<Object[]> result = stockIntegrityJpaRepository.findOrphanSkus(PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    /** Colunas por posição, na ordem declarada na query nativa. */
    private OrphanSku toDomain(Object[] row) {
        return OrphanSku.of(
                (String) row[0],
                (String) row[1],
                toBigDecimal(row[2]),
                toLong(row[3]),
                toBoolean(row[4]),
                toInstant(row[5]));
    }

    /** Postgres e H2 devolvem {@code BOOLEAN}, mas há driver que materializa o CASE como 1/0. */
    private boolean toBoolean(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean bool -> bool;
            case Number number -> number.intValue() != 0;
            default -> Boolean.parseBoolean(value.toString());
        };
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    /**
     * {@code created_at} é {@code TIMESTAMP} sem fuso, e a query nativa devolve o tipo cru do
     * driver — {@link Timestamp} no Postgres e no H2, mas não há garantia disso em todo driver.
     * Daí a conversão defensiva. {@code Timestamp.toInstant()} usa o fuso default da JVM, que é
     * exatamente o que o Hibernate usa ao gravar o {@code Instant} da entidade: a ida e a volta
     * são simétricas dentro do mesmo processo.
     */
    private Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Timestamp timestamp -> timestamp.toInstant();
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case LocalDateTime localDateTime -> localDateTime.atZone(ZoneId.systemDefault()).toInstant();
            default -> throw new IllegalStateException(
                    "Tipo inesperado para last_movement_at: " + value.getClass().getName());
        };
    }
}
