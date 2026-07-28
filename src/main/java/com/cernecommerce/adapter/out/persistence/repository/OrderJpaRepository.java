package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
