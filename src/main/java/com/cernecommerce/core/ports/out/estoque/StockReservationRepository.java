package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockReservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência das reservas de estoque (EST-F021).
 */
public interface StockReservationRepository {

    StockReservation save(StockReservation reservation);

    Optional<StockReservation> findById(Long id);

    /**
     * Reservas <b>ativas</b> de um mesmo dono. É o que permite liberar ou consumir de uma vez as
     * várias linhas de um pedido, sem o chamador ter que guardar os ids item a item.
     */
    List<StockReservation> findActiveByOwnerReference(String ownerReference);

    /**
     * Listagem filtrada, mais recentes primeiro. Qualquer um dos filtros pode ser {@code null},
     * significando "não filtrar por este campo".
     */
    PageResult<StockReservation> findByFilters(String sku, Long warehouseId, ReservationStatus status,
            int page, int size);

    /**
     * Reservas ativas cujo {@code expiresAt} já passou em {@code now}, no máximo {@code limit}.
     *
     * <p>O limite existe para o varredor trabalhar em lotes: uma janela de indisponibilidade longa
     * pode acumular muita reserva vencida, e carregar tudo de uma vez transformaria a limpeza no
     * próprio incidente.</p>
     */
    List<StockReservation> findExpired(Instant now, int limit);
}
